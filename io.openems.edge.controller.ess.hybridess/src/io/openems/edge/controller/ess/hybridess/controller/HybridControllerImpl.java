package io.openems.edge.controller.ess.hybridess.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;

import io.openems.edge.controller.ess.hybridess.prediction.PredictionCSV;
import io.openems.edge.controller.ess.hybridess.prediction.PredictionCSV.Row;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.InvalidValueException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.CalculateGridMode;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid;
import io.openems.edge.meter.api.SymmetricMeter;


@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Symmetric.Hybrid", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class HybridControllerImpl extends AbstractOpenemsComponent implements HybridController, Controller, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(HybridControllerImpl.class);
		
	private Config config = null;
	
	private File energyPredictionFile;
	private File powerPredictionFile;

	private PredictionCSV.Row lastPowerPrediction;
	private PredictionCSV.Row lastEnergyPrediction;
	
	/**
	 * Minimal total Energy that should be stored by 
	 * ESSs to ensure EVs can be serviced.
	 */
	private int defaultMinimumEnergy;
	private int maxGridPower;

	// Percentage of mainEss's maximum power output, that mainEss should supply alone as netpower.
	private final double netPowerThreshold = 0.8;

	@Reference
	private ComponentManager componentManager;
	
	@Reference 
	private Sum sum;

	public HybridControllerImpl(){
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				HybridController.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this.energyPredictionFile = Path.of(config.energyPrediction()).toFile();
		this.powerPredictionFile = Path.of(config.powerPrediction()).toFile();
		this.defaultMinimumEnergy = config.defaultMinimumEnergy();
		this.maxGridPower = config.maxGridPower();

		if(!Files.exists(energyPredictionFile.toPath())) {
			this.logInfo(log, String.format("Energy prediction at %s not found", energyPredictionFile.toPath()));
		}
		
		if(!Files.exists(powerPredictionFile.toPath())) {
			this.logInfo(log, String.format("Power prediction at %s not found", powerPredictionFile.toPath()));
		}
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		ManagedSymmetricEssHybrid mainEss = componentManager.getComponent(config.mainId());
		ManagedSymmetricEssHybrid supportEss  = componentManager.getComponent(config.supportId());
		GridMode gridMode = CalculateGridMode.calculate(Arrays.asList(mainEss.getGridMode(), supportEss.getGridMode()));

		switch(gridMode) {
		case UNDEFINED:
			this.logWarn(this.log, "Grid-Mode is [UNDEFINED]");
		case ON_GRID:
			break;
		case OFF_GRID:
		default:
			return;
		}
		
		// TODO Handling of different operating status.
		if(sum.getConsumptionActivePower().orElse(0) > 0) {
			this.discharge(mainEss, supportEss);
		} else {
			this.charge(mainEss, supportEss);
		}
	}

	private void discharge(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws OpenemsNamedException {

		// ConsumptionActivePower has to be defined at this point, as it is checked before calling discharge.
		int requiredPower = sum.getConsumptionActivePower().get();
		double powerSplit = 1;
		if(requiredPower >= netPowerThreshold*mainEss.getMaxApparentPower().orElse(0)
			|| !SoCArea.getArea(mainEss, defaultMinimumEnergy).equals(SoCArea.GREEN)) {

			// TODO Implement for discharge. No numbers yet.
			powerSplit = dischargePowerSplit(mainEss, supportEss);
		}

		int mainEssPower = mainEss.filterPower((int) (powerSplit * requiredPower));
		int liIonPower = supportEss.filterPower(requiredPower - mainEssPower);

		mainEss.setActivePowerEquals(mainEssPower);
		supportEss.setActivePowerEquals(liIonPower);

		mainEss.setReactivePowerEquals(0);
		supportEss.setReactivePowerEquals(0);
	}
	
	private void charge(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws OpenemsNamedException {
		int targetGridSetPoint;
		int minimumStoredEnergy = getMinimumStoredEnergy();
		int totalStoredEnergy = this.getTotalStoredEnergy(mainEss, supportEss);
		LocalDateTime now = LocalDateTime.now(componentManager.getClock());

		if(minimumStoredEnergy >= totalStoredEnergy) {
			targetGridSetPoint = calculateGridSetPoint(minimumStoredEnergy, totalStoredEnergy, now);
		} else if (SoCArea.getArea(mainEss,defaultMinimumEnergy).equals(SoCArea.RED)
					|| SoCArea.getArea(supportEss, defaultMinimumEnergy).equals(SoCArea.RED)) {
			targetGridSetPoint = -this.maxGridPower;
		} else {
			targetGridSetPoint = getPredictedPower();
		}
		
		if(targetGridSetPoint > 0) {
			
			/* 
			 * TODO In this case Prediction recommends discharging.
			 * In this case this branch should probably not even be entered.
			 * As interim solution: set to 0
			 */
			logInfo(log, String.format("Ignored power prediction %s during charge at %s",
					targetGridSetPoint, now));
			targetGridSetPoint = 0;
		}

		int chargePower = targetGridSetPoint - this.getTotalProductionPower();

		double powerSplit = chargePowerSplit(mainEss, supportEss);

		int mainPower = mainEss.filterPower((int) (powerSplit*chargePower));
		int supportPower = supportEss.filterPower(chargePower-mainPower);
		
		mainEss.setActivePowerEquals(mainPower);
		supportEss.setActivePowerEquals(supportPower);
		
		mainEss.setReactivePowerEquals(0);
		supportEss.setReactivePowerEquals(0);
	}
	
	/**
	 * Determines amount of power that should be feed into or sold to grid, based on predicted supply and demand
	 * and current Energy Prices. 
	 * 
	 * @return Value in [W] which should be feed-in(positive) 
	 * 	      or sold-off(negative) to grid.
	 */
	private int calculateGridSetPoint(int minimumStoredEnergy, int totalStoredEnergy, LocalDateTime now) {
		double missingEnergy = minimumStoredEnergy - totalStoredEnergy;
		int targetGridSetPoint = -maxGridPower;
		if(lastEnergyPrediction != null) {
			Duration remainingTime = Duration.between(now, lastEnergyPrediction.getEnd());
			targetGridSetPoint = -Math.min(powerFromEnergy(missingEnergy, remainingTime), maxGridPower);
		}
		return targetGridSetPoint;
	}
	
	/**
	 * Calculate the power needed to balance grid feed-in of {@code meter} to {@code targetGridSetpoint},
	 * using the sum of the active power of all currently active ESS.
	 *  
	 * @param meter the meter which should be balanced.
	 * @param targetGridSetpoint Value in [W] which should be feed-in(positive) 
	 * 	      or sold-off(negative) to grid.
	 * @return Value in [W] determining the amount of power the ESSs need to charge(negative) 
	 * 		   or discharge(positive) to balance the meter.
	 * @throws InvalidValueException if active Power of meter or the ESSs is {@code null}.
	 */
	private int calculatePower(SymmetricMeter meter, int targetGridSetpoint) throws InvalidValueException {
		return meter.getActivePower().getOrError() /* current buy-from/sell-to grid */
				+ sum.getEssActivePower().getOrError() /* current charge/discharge of ALL Ess */
				- targetGridSetpoint; /* the configured target setpoint */
	}
	
	private int getMinimumStoredEnergy() {
		if(lastEnergyPrediction == null
				|| lastEnergyPrediction.getEnd().isAfter(LocalDateTime.now(componentManager.getClock()))) {
			Optional<Row> prediction = getPrediction(energyPredictionFile);
			lastEnergyPrediction = prediction.orElse(null);
		}

		return lastEnergyPrediction == null ? defaultMinimumEnergy : lastEnergyPrediction.getValue();
	}

	private int getPredictedPower() {
		if(lastPowerPrediction == null
				|| lastPowerPrediction.getEnd().isAfter(LocalDateTime.now(componentManager.getClock()))) {
			Optional<Row> prediction = getPrediction(powerPredictionFile);
			lastPowerPrediction = prediction.orElse(null);
		}
		return lastPowerPrediction == null ? 0 : lastPowerPrediction.getValue();
	}

	private Optional<Row> getPrediction(File powerPredictionFile) {
		LocalDateTime now = LocalDateTime.now(componentManager.getClock());
		return PredictionCSV.getPrediction(powerPredictionFile, now);
	}

	private int getTotalStoredEnergy(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws InvalidValueException {
		int mainEssStoredEnergy = (mainEss.getCapacity().getOrError() * (mainEss.getSoc().getOrError()));
		int liIonStoredEnergy = (supportEss.getCapacity().getOrError() * (supportEss.getSoc().getOrError()));
		return (mainEssStoredEnergy + liIonStoredEnergy) / 100;
	}
	
	private int getTotalProductionPower() {
		int totalProductionEnergy = 0;
		if(sum.getProductionActivePower().isDefined()) {
			totalProductionEnergy = sum.getProductionActivePower().get();
		} else {
			logInfo(log, "Could not calculate ProductionPower correctly. Capacity Channel undefinded.");
		}
		return totalProductionEnergy;
	}


	private double chargePowerSplit(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws InvalidValueException {
		double powerSplit;
		SoCArea mainEssArea = SoCArea.getArea(mainEss, defaultMinimumEnergy);
		SoCArea supportEssArea = SoCArea.getArea(supportEss, defaultMinimumEnergy);
		powerSplit = SoCArea.getPowerSplitCharging(mainEssArea, supportEssArea);
		return powerSplit;
	}

	private double dischargePowerSplit(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws InvalidValueException {
		double powerSplit;
		SoCArea mainEssArea = SoCArea.getArea(mainEss, defaultMinimumEnergy);
		SoCArea supportEssArea = SoCArea.getArea(supportEss, defaultMinimumEnergy);
		powerSplit = SoCArea.getPowerSplitDischarging(mainEssArea, supportEssArea);
		return powerSplit;
	}

	/**
	 * Calculates the amount of power required to reach {@code energyRequirement} in
	 * time {@code duration}.
	 * Power = energyRequirement / Duration
	 *
	 * @param energyRequirement target amount of power in [Wh].
	 * @param duration	time remaining to charge/ discharge.
	 * @return Power in [W] needed to reach {@code energyRequirement} in time {@code duration}.
	 */
	private int powerFromEnergy(double energyRequirement, Duration duration) {
		if(duration.isNegative() || duration.isZero()) {
			return Integer.MAX_VALUE;
		}
		return (int)((energyRequirement * 3600.0) / duration.toSeconds());
	}

	private enum SoCArea {
		RED,
		ORANGE,
		GREEN;

		/*
		 * Distribution of available charge power between the two ESSs based
		 * on the SoC.
		 * ChargeTable[SocAreaSupport][SocAreaMain] = % of available power to be assigned to main.
		 */
		private final static double[][] CHARGE_TABLE = {{0.5, 0.3, 0},
													   {0.7, 0.5, 0.2},
				                                       {1.0, 0.8, 0.5}};
		private final static double[][] DISCHARGE_TABLE = {{0.5, 0.8, 1.0},
				 										  {0.2, 0.5, 0.7},
				                                          {0, 0.3, 0.5}};

		/*
		 * TODO do we need to separate methods for the ESSs? If so this should maybe a method of the ESS.
		 * But from my POV this should be controlled by controller.
		 */
		private static SoCArea getArea(ManagedSymmetricEss ess, int minimumEnergy) throws InvalidValueException {
			SoCArea SoCArea;
			int storedEnergy = ess.getCapacity().getOrError() * (ess.getSoc().getOrError()) / 100;
			if (storedEnergy <= 0.5 * minimumEnergy) {
				SoCArea = HybridControllerImpl.SoCArea.RED;
			} else if( storedEnergy <= 0.5* ess.getCapacity().getOrError()) {
				SoCArea = HybridControllerImpl.SoCArea.ORANGE;
			} else {
				SoCArea= HybridControllerImpl.SoCArea.GREEN;
			}
			return SoCArea;
		}

		private static double getPowerSplitCharging(SoCArea mainSocArea, SoCArea supportSocArea) {
			return CHARGE_TABLE[supportSocArea.ordinal()][mainSocArea.ordinal()];
		}

		private static double getPowerSplitDischarging(SoCArea mainSocArea, SoCArea supportSocArea) {
			return DISCHARGE_TABLE[supportSocArea.ordinal()][mainSocArea.ordinal()];
		}
	}
}

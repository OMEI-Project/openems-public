package io.openems.edge.controller.ess.hybridess.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
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


@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Symmetric.Hybrid", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class HybridControllerImpl extends AbstractOpenemsComponent implements HybridController, Controller, OpenemsComponent {

	/**
	 * Distribution of available charge power between the two ESSs based
	 * on the SoC.
	 * ChargeTable[SocAreaSupport][SocAreaMain] = % of available power to be assigned to main.
	 */
	private final static double[][] CHARGE_TABLE =
			{{0.5, 0.3, 0},
			{0.7, 0.7, 0.2},
			{1.0, 0.8, 0.5}};

	/**
	 * Distribution of required discharge power between the two ESSs based
	 * on the SoC.
	 * ChargeTable[SocAreaSupport][SocAreaMain] = % of required power drawn from main.
	 */
	private final static double[][] DISCHARGE_TABLE =
			{{0.5, 0.8, 1.0},
			{0.2, 0.5, 0.7},
			{0, 0.3, 0.5}};

	/**
	 * Boundary of SoC-Areas for main.
	 * Below {@code  MAIN_SOC_BOUNDARIES[0]} : RED AREA.
	 * Between {@code  MAIN_SOC_BOUNDARIES[0]} and {@code  MAIN_SOC_BOUNDARIES[1]} : ORANGE AREA
	 * Above {@code  MAIN_SOC_BOUNDARIES[1]}: GREEN AREA
	 */
	private final static int[] MAIN_SOC_BOUNDARIES = {20,70};

	/**
	 * Boundary of SoC-Areas for support.
	 * Below {@code  SUPPORT_SOC_BOUNDARIES[0]} : RED AREA.
	 * Between {@code  SUPPORT_SOC_BOUNDARIES[0]} and {@code  SUPPORT_SOC_BOUNDARIES[1]} : ORANGE AREA
	 * Above {@code  SUPPORT_SOC_BOUNDARIES[1]}: GREEN AREA
	 */
	private final static int[] SUPPORT_SOC_BOUNDARIES = {20, 50};
	private final Logger log = LoggerFactory.getLogger(HybridControllerImpl.class);
		
	private Config config = null;

	private File energyPredictionFile;
	private File powerPredictionFile;

	private PredictionCSV.Row lastPowerPrediction;
	private PredictionCSV.Row lastEnergyPrediction;

	private static final PredictionCSV.Row DUMMY_PREDICTION = new PredictionCSV.Row(
			LocalDateTime.of(0, Month.JANUARY,1,0,0,0),
			LocalDateTime.of(0, Month.JANUARY,1,0,0,1),
			0);
	
	/**
	 * Minimum total Energy that should be stored by
	 * ESSs to ensure EVs can be serviced.
	 */
	private int defaultMinimumEnergy;

	/**
	 * Maximum power that can be drawn from grid.
	 */
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


	/**
	 * Calculates the power required this cycle and a distributes the load between {@code main} and {@code support}
	 * based on the ESSs SoC and the total power required.
	 *
	 * @param mainEss ESS that should cover the netload.
	 * @param supportEss ESS that should cover peak loads.
	 * @throws OpenemsNamedException on error.
	 */
	private void discharge(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws OpenemsNamedException {

		// ConsumptionActivePower has to be defined at this point, as it is checked before calling discharge.
		int requiredPower = sum.getConsumptionActivePower().get() - sum.getProductionActivePower().orElse(0);
		double powerSplit = 1;

		if(requiredPower >= netPowerThreshold*mainEss.getMaxApparentPower().orElse(0)
			|| !SoCArea.getArea(mainEss, MAIN_SOC_BOUNDARIES).equals(SoCArea.GREEN)) {
			powerSplit = dischargePowerSplit(mainEss, supportEss);
		}

		int mainEssPower = mainEss.filterPower((int) (powerSplit * requiredPower));
		int supportEssPower = supportEss.filterPower(requiredPower - mainEssPower);

		int remainder = requiredPower - mainEssPower - supportEssPower;
		if(remainder > 0) {
			mainEssPower = mainEss.filterPower(mainEssPower + remainder);
		}

		mainEss.setActivePowerEquals(mainEssPower);
		supportEss.setActivePowerEquals(supportEssPower);

		mainEss.setReactivePowerEquals(0);
		supportEss.setReactivePowerEquals(0);
	}

	/**
	 * Calculated power available for charging of the ess and distributes it across the ESS based on their
	 * SoC.
	 *
	 * @param mainEss ESS that should cover the netload.
	 * @param supportEss ESS that should cover peak loads.
	 * @throws OpenemsNamedException on error.
	 */
	private void charge(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws OpenemsNamedException {
		int targetGridSetPoint;
		int minimumStoredEnergy = getTargetStoredEnergy();
		int totalStoredEnergy = this.getTotalStoredEnergy(mainEss, supportEss);
		LocalDateTime now = LocalDateTime.now(componentManager.getClock());

		if(minimumStoredEnergy >= totalStoredEnergy) {
			targetGridSetPoint = calculateGridSetPoint(minimumStoredEnergy, totalStoredEnergy, now);
		} else if (SoCArea.getArea(mainEss, MAIN_SOC_BOUNDARIES).equals(SoCArea.RED)
					|| SoCArea.getArea(supportEss, SUPPORT_SOC_BOUNDARIES).equals(SoCArea.RED)) {
			targetGridSetPoint = -this.maxGridPower;
		} else {
			targetGridSetPoint = getPredictedPower();
		}
		
		if(targetGridSetPoint > 0) {
			logInfo(log, String.format("Ignored power prediction %s during charge at %s",
					targetGridSetPoint, now));
			targetGridSetPoint = 0;
		}

		int chargePower = targetGridSetPoint - sum.getProductionActivePower().orElse(0);

		double powerSplit = chargePowerSplit(mainEss, supportEss);

		int mainEssPower = mainEss.filterPower((int) (powerSplit*chargePower));
		int supportEssPower = supportEss.filterPower(chargePower-mainEssPower);
		int remainder = chargePower - mainEssPower - supportEssPower;

		if(remainder < 0) {
			mainEssPower = mainEss.filterPower(mainEssPower + remainder);
		}
		
		mainEss.setActivePowerEquals(mainEssPower);
		supportEss.setActivePowerEquals(supportEssPower);
		
		mainEss.setReactivePowerEquals(0);
		supportEss.setReactivePowerEquals(0);
	}
	
	/**
	 * Determines amount of power that should be feed into or sold to grid, based on predicted supply and demand
	 * and current Energy Prices.
	 *
	 * @param targetStoredEnergy Target amount of energy in [Wh] that should be stored.
	 * @param totalStoredEnergy Amount of energy currently stored in across all ess controlles by {@code this}
	 * @param now timestamp of this cycle.
	 * @return Value in [W] which should be feed-in(positive) 
	 * 	      or sold-off(negative) to grid.
	 */
	private int calculateGridSetPoint(int targetStoredEnergy, int totalStoredEnergy, LocalDateTime now) {
		double missingEnergy = targetStoredEnergy - totalStoredEnergy;
		int targetGridSetPoint = -maxGridPower;
		if(lastEnergyPrediction != null) {
			Duration remainingTime = Duration.between(now, lastEnergyPrediction.getEnd());
			targetGridSetPoint = -Math.min(powerFromEnergy(missingEnergy, remainingTime), maxGridPower);
		}
		return targetGridSetPoint;
	}

	/**
	 * Calculates target amount of energy that should be stored across the ESS controlled by {@code this}.
	 * Set based on a energy prediction. Otherwise {@code defaultMinimumEnergy is used}.
	 *
	 * @return Target amount of energy that should be stored.
	 */
	private int getTargetStoredEnergy() {
		if(lastEnergyPrediction == null
				|| lastEnergyPrediction.getEnd().isBefore(LocalDateTime.now(componentManager.getClock()))) {
			Optional<Row> prediction = getPrediction(energyPredictionFile);
			lastEnergyPrediction = prediction.orElse(null);
		}

		return lastEnergyPrediction == null ? defaultMinimumEnergy : lastEnergyPrediction.getValue();
	}

	/**
	 * Checks whether there exists a prediction dictating what amount of power should be drawn from grid in this cycle.
	 * If no prediction exists for this cycle returns 0.
	 *
	 * @return Amount of power that should be drawn from grid based on prediction. If no prediction exist 0.
	 */
	private int getPredictedPower() {
		if(lastPowerPrediction == null
				|| lastPowerPrediction.getEnd().isBefore(LocalDateTime.now(componentManager.getClock()))) {
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

	private double chargePowerSplit(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws InvalidValueException {
		double powerSplit;
		SoCArea mainEssArea = SoCArea.getArea(mainEss, MAIN_SOC_BOUNDARIES);
		SoCArea supportEssArea = SoCArea.getArea(supportEss, SUPPORT_SOC_BOUNDARIES);
		powerSplit = getPowerSplitCharging(mainEssArea, supportEssArea);
		return powerSplit;
	}

	private double dischargePowerSplit(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws InvalidValueException {
		double powerSplit;
		SoCArea mainEssArea = SoCArea.getArea(mainEss, MAIN_SOC_BOUNDARIES);
		SoCArea supportEssArea = SoCArea.getArea(supportEss, SUPPORT_SOC_BOUNDARIES);
		powerSplit = getPowerSplitDischarging(mainEssArea, supportEssArea);
		return powerSplit;
	}

	private static double getPowerSplitCharging(SoCArea mainSocArea, SoCArea supportSocArea) {
		return CHARGE_TABLE[supportSocArea.ordinal()][mainSocArea.ordinal()];
	}

	private static double getPowerSplitDischarging(SoCArea mainSocArea, SoCArea supportSocArea) {
		return DISCHARGE_TABLE[supportSocArea.ordinal()][mainSocArea.ordinal()];
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

		private static SoCArea getArea(ManagedSymmetricEss ess, int[] boundaries) throws InvalidValueException {
			SoCArea SoCArea;
			int soc = ess.getSoc().getOrError();
			int storedEnergy = ess.getCapacity().getOrError() * (ess.getSoc().getOrError()) / 100;
			if (soc <= boundaries[0] ) {
				SoCArea = HybridControllerImpl.SoCArea.RED;
			} else if(soc <= boundaries[1]) {
				SoCArea = HybridControllerImpl.SoCArea.ORANGE;
			} else {
				SoCArea= HybridControllerImpl.SoCArea.GREEN;
			}
			return SoCArea;
		}
	}
}

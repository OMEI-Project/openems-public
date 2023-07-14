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
import io.openems.edge.ess.api.managedsymmetricesshybrid.ManagedSymmetricEssHybrid;
import io.openems.edge.ess.api.managedsymmetricesshybrid.SocState;


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
	 * ChargeTable[SocStateSupport][SocState] = % of available power to be assigned to main.
	 */
	private final static double[][] CHARGE_TABLE =
			{{0.5, 0.3, 0},
			{0.7, 0.7, 0.2},
			{1.0, 0.8, 0.5}};

	/**
	 * Distribution of required discharge power between the two ESSs based
	 * on the SoC.
	 * ChargeTable[SocStateSupport][SocStateMain] = % of required power drawn from main.
	 */
	private final static double[][] DISCHARGE_TABLE =
			{{0.5, 0.8, 1.0},
			{0.2, 0.7, 0.7},
			{0, 0.3, 0.7}};

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

	private String mainId;
	private String supportId;

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

	public HybridControllerImpl(String energyPrediction, String powerPrediction,
								int defaultMinimumEnergy, int maxGridPower, String mainId, String supportId,
								Sum sum, ComponentManager componentManager){
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				HybridController.ChannelId.values() //
		);
		this.sum = sum;
		this.componentManager = componentManager;
		internalActivate(energyPrediction, powerPrediction, defaultMinimumEnergy, maxGridPower, mainId, supportId);
	}

	public HybridControllerImpl(){
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				HybridController.ChannelId.values() //
		);
	}

	private void internalActivate(String energyPrediction, String powerPrediction,
							 int defaultMinimumEnergy, int maxGridPower, String mainId, String supportId) {
		this.energyPredictionFile = Path.of(energyPrediction).toFile();
		this.powerPredictionFile = Path.of(powerPrediction).toFile();
		this.defaultMinimumEnergy = defaultMinimumEnergy;
		this.maxGridPower = maxGridPower;
		this.mainId = mainId;
		this.supportId=supportId;

		if(!Files.exists(energyPredictionFile.toPath())) {
			this.logInfo(log, String.format("Energy prediction at %s not found", energyPredictionFile.toPath()));
		}

		if(!Files.exists(powerPredictionFile.toPath())) {
			this.logInfo(log, String.format("Power prediction at %s not found", powerPredictionFile.toPath()));
		}
	}
	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		internalActivate(config.energyPrediction(), config.powerPrediction(), config.defaultMinimumEnergy(),
				config.maxGridPower(), config.mainId(), config.supportId());
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}


	@Override
	public void run() throws OpenemsNamedException {
		ManagedSymmetricEssHybrid mainEss = componentManager.getComponent(mainId);
		ManagedSymmetricEssHybrid supportEss  = componentManager.getComponent(supportId);
		GridMode gridMode = CalculateGridMode.calculate(Arrays.asList(mainEss.getGridMode(), supportEss.getGridMode()));
		SocState mainSocState = mainEss.getSocState();
		SocState supportSocState = supportEss.getSocState();

		final int consumption = sum.getConsumptionActivePower().orElse(0);
		final int production = sum.getProductionActivePower().orElse(0);
		int totalStoredEnergy = this.getTotalStoredEnergy(mainEss, supportEss);
		int energyPrediction = getEnergyPrediction();
		int essPower = consumption
				- production - getPowerPrediction();
		double powerSplit = 1.0;
		switch(gridMode) {
		case UNDEFINED:
			this.logWarn(this.log, "Grid-Mode is [UNDEFINED]");
		case ON_GRID:
			break;
		case OFF_GRID:
		default:
			throw new IllegalStateException(String.format("Unknown state %s for grid mode of ess.", gridMode));
		}


		if(mainSocState == SocState.RED && supportSocState == SocState.RED
				|| defaultMinimumEnergy >= totalStoredEnergy) {

			// Use grid to meet demand/ charge the ess as well. In case of depleted ESS or minimum energy not met.
			essPower = consumption - (production + maxGridPower);
		} else if (totalStoredEnergy <= energyPrediction) {
			essPower = calculateGridSetPoint(energyPrediction, totalStoredEnergy,
						LocalDateTime.now(componentManager.getClock())) - production;
		} else if (mainSocState == SocState.RED) { // Special cases discharging: one ess in RED
			conserveRed(supportEss, mainEss, supportSocState);
			return;
		} else if (supportSocState == SocState.RED) {
			conserveRed(mainEss, supportEss, mainSocState);
			return;
		}

		if(essPower <= 0) { // charging.
			powerSplit = chargePowerSplit(mainEss, supportEss);
		}  else { // Discharging
			powerSplit = dischargePowerSplit(mainEss, supportEss, essPower);
		}

		int mainEssPower = mainEss.filterPower((int) (powerSplit * essPower));
		int supportEssPower = supportEss.filterPower(essPower - mainEssPower);

		int remainder = essPower - mainEssPower - supportEssPower;
		mainEssPower = mainEss.filterPower(mainEssPower + remainder);

		if (consumption > maxGridPower + production + mainEssPower + supportEssPower) {

			// TODO Load shedding. Handling of this case. For now just let it happen.
			logInfo(log,String.format("Consumption of %s W would not be met with a limited grid " +
					"by a total of %s W supplied from grid, production and ESS. Load shedding required for future.",
					consumption, maxGridPower + production + mainEssPower + supportEssPower ));
		}

		mainEss.setActivePowerEquals(mainEssPower);
		supportEss.setActivePowerEquals(supportEssPower);

		mainEss.setReactivePowerEquals(0);
		supportEss.setReactivePowerEquals(0);
	}

	private void conserveRed(ManagedSymmetricEssHybrid activeEss, ManagedSymmetricEssHybrid conservedEss, SocState activeSocState)
			throws OpenemsNamedException {
		int conservedEssPower = conservedEss.getUpperPossibleDischargePower().orElse(0);
		int activeEssPower = activeEss.getUpperPossibleDischargePower().orElse(0);
		int consumption = sum.getConsumptionActivePower().orElse(0);
		int production = sum.getProductionActivePower().orElse(0);
		int essPower = consumption
				- production
				- maxGridPower;

		if (essPower <= 0) {

			// Demand can be met by grid + production; Charge Ess with remainder, still supply as much power as possible from other ess
			activeEssPower = 0;
			conservedEssPower = essPower;
		} else if (activeEssPower >= essPower){

			// Demand can be met by grid + production + ess not in red. Do not discharge ess in red.
			activeEssPower = activeSocState == SocState.GREEN ? consumption - production : essPower;
			conservedEssPower = 0;
		} else if (activeEssPower + conservedEssPower >= essPower) {

			// Demand can be met by grid + production + both ess.
			conservedEssPower = essPower - activeEssPower;
		} else if (activeEssPower + conservedEssPower < essPower) {

			// In these case some kind of load shedding should happen, as load can not be served by available power sources.
			logInfo(log,String.format("Consumption of %s W would not be met with a limited grid " +
							"by a total of %s W supplied from grid, production and ESS. Load shedding required for future.",
					consumption, maxGridPower + production + activeEssPower + conservedEssPower ));
		}

		activeEssPower = activeEss.filterPower(activeEssPower);
		conservedEssPower = conservedEss.filterPower(conservedEssPower);

		int remainder = essPower - activeEssPower - conservedEssPower;

		if( consumption + conservedEssPower < production ) { // if conservedEss & consumption is charged solely on production power, reassign remainder.
			activeEssPower = activeEss.filterPower(activeEssPower + remainder);
		}

		conservedEss.setActivePowerEquals(conservedEssPower);
		activeEss.setActivePowerEquals(activeEssPower);
		conservedEss.setReactivePowerEquals(0);
		activeEss.setReactivePowerEquals(0);
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
	private int getEnergyPrediction() {
		if(lastEnergyPrediction == null
				|| lastEnergyPrediction.getEnd().isBefore(LocalDateTime.now(componentManager.getClock()))) {
			Optional<Row> prediction = getPrediction(energyPredictionFile);
			lastEnergyPrediction = prediction.orElse(null);
		}

		return lastEnergyPrediction == null ? 0 : lastEnergyPrediction.getValue();
	}

	/**
	 * Checks whether there exists a prediction dictating what amount of power should be drawn from grid in this cycle.
	 * If no prediction exists for this cycle returns 0.
	 *
	 * @return Amount of power that should be drawn from grid based on prediction. If no prediction exist 0.
	 */
	private int getPowerPrediction() {
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
		SocState mainEssSocState = mainEss.getSocState();
		SocState supportSocState = supportEss.getSocState();
		powerSplit = getPowerSplitCharging(mainEssSocState, supportSocState);
		return powerSplit;
	}

	private double dischargePowerSplit(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss, int essPower) throws InvalidValueException {
		double powerSplit = 1;
		SocState mainSocState = mainEss.getSocState();
		SocState supportSocState = supportEss.getSocState();
		if(essPower >= netPowerThreshold*mainEss.getMaxApparentPower().orElse(0)
				|| !mainSocState.equals(SocState.GREEN)) {
			powerSplit = getPowerSplitDischarging(mainSocState, supportSocState);
		}
		return powerSplit;
	}

	private static double getPowerSplitCharging(SocState mainSocState, SocState supportSocState) {
		return CHARGE_TABLE[supportSocState.ordinal()][mainSocState.ordinal()];
	}

	private static double getPowerSplitDischarging(SocState mainSocState, SocState supportSocState) {
		return DISCHARGE_TABLE[supportSocState.ordinal()][mainSocState.ordinal()];
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
		if(duration == null || duration.isNegative() || duration.isZero()) {
			return Integer.MAX_VALUE;
		}
		return (int)((energyRequirement * 3600.0) / duration.toSeconds());
	}
}

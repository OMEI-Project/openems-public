package io.openems.edge.controller.ess.hybridess.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SoCStateMachine;
import io.openems.edge.ess.api.SocState;


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
			{{0.5, 0.7, 1.0},
			{0.3, 0.7, 0.7},
			{0, 0.3, 0.7}};

	private final Logger log = LoggerFactory.getLogger(HybridControllerImpl.class);

	private boolean enableDualBatteryMode;
	private String mainId;
	private String supportId;
	private String dataAcquisitionServiceBaseUrl;
	
	/**
	 * Flag to enable/disable minimum energy function
	 */
	private boolean enableMinimumEnergyFunction;
	
	/**
	 * Minimum total Energy that should be stored by
	 * ESS to ensure EVs can be serviced.
	 */
	private int defaultMinimumEnergy;

	/**
	 * Maximum power that can be drawn from grid.
	 */
	private int maxGridPower;

	/**
	 * Interval in controller cycles between communications with the data acquisition service.
	 */
	private int dataServiceInterval;

	/**
	 * SoC State Machine for managing battery state transitions
	 */
	private SoCStateMachine socStateMachine;

	/**
	 * Maximum power change per cycle for power filtering/ramping
	 */
	private int maxPowerChangePerCycle;

	/**
	 * Last power value for power filtering/ramping
	 */
	private int lastPower = 0;

	/**
	 * Maximum charge power in W (stored as negative value)
	 */
	private int maxChargePower;

	/**
	 * Maximum discharge power in W
	 */
	private int maxDischargePower;

	/**
	 * Efficiency lookup tables for charging and discharging
	 */
	private EfficiencyTable chargingEfficiencyTable;
	private EfficiencyTable dischargingEfficiencyTable;

	/**
	 * Battery efficiency multipliers
	 */
	private double batteryChargingEfficiency;
	private double batteryDischargingEfficiency;

	/**
	 * Percentage of mainEss's maximum power output, that mainEss should supply alone as netpower.
	 * Used in dual-battery mode for power distribution logic.
	 */
	private final double netPowerThreshold = 0.8;

	private int flaskSendCounter = 0;

	@Reference
	private ComponentManager componentManager;
	
	@Reference 
	private Sum sum;

	// Constructor for testing
	public HybridControllerImpl(int defaultMinimumEnergy, int maxGridPower, String mainId, String supportId,
								String dataAcquisitionServiceBaseUrl,
								Sum sum, ComponentManager componentManager){
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				HybridController.ChannelId.values() //
		);
		this.sum = sum;
		this.componentManager = componentManager;
		// Determine dual battery mode from main battery ID configuration
		boolean enableDualBatteryMode = mainId != null && !mainId.trim().isEmpty();
		internalActivate(enableDualBatteryMode, false, defaultMinimumEnergy, maxGridPower, mainId, supportId, dataAcquisitionServiceBaseUrl, 10,
				new int[]{10, 20}, new int[]{85, 95}, 1000, 276_000, 276_000,
				new double[]{0.0, 0.5, 1.0}, new double[]{0.85, 0.90, 0.85},
				new double[]{0.0, 0.5, 1.0}, new double[]{0.85, 0.90, 0.85},
				0.95, 0.95);
	}

	public HybridControllerImpl(){
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				HybridController.ChannelId.values() //
		);
	}

	private void internalActivate(boolean enableDualBatteryMode, boolean enableMinimumEnergyFunction, int defaultMinimumEnergy, int maxGridPower, String mainId, String supportId,
							 String dataAcquisitionServiceBaseUrl, int dataServiceInterval,
							 int[] lowerSocBounds, int[] upperSocBounds, int maxPowerChangePerCycle,
							 int maxChargePower, int maxDischargePower,
							 double[] chargingEfficiencyKeys, double[] chargingEfficiencyValues,
							 double[] dischargingEfficiencyKeys, double[] dischargingEfficiencyValues,
							 double batteryChargingEfficiency, double batteryDischargingEfficiency) {
		this.enableDualBatteryMode = enableDualBatteryMode;
		this.enableMinimumEnergyFunction = enableMinimumEnergyFunction;
		this.defaultMinimumEnergy = defaultMinimumEnergy;
		this.maxGridPower = maxGridPower;
		this.mainId = mainId;
		this.supportId = supportId;
		this.dataAcquisitionServiceBaseUrl = dataAcquisitionServiceBaseUrl;
		this.dataServiceInterval = dataServiceInterval;
		this.maxPowerChangePerCycle = maxPowerChangePerCycle;
		this.maxChargePower = -Math.abs(maxChargePower); // Store as negative
		this.maxDischargePower = maxDischargePower;
		this.batteryChargingEfficiency = batteryChargingEfficiency;
		this.batteryDischargingEfficiency = batteryDischargingEfficiency;
		
		// Initialize efficiency tables
		this.chargingEfficiencyTable = new EfficiencyTable(chargingEfficiencyKeys, chargingEfficiencyValues);
		this.dischargingEfficiencyTable = new EfficiencyTable(dischargingEfficiencyKeys, dischargingEfficiencyValues);
		
		// Initialize SoC state machine
		this.socStateMachine = new SoCStateMachine(lowerSocBounds, upperSocBounds);
		this.lastPower = 0;
	}
	
	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		internalActivate(config.enableDualBatteryMode(), config.enableMinimumEnergyFunction(), config.defaultMinimumEnergy(),
				config.maxGridPower(), config.mainId(), config.supportId(), config.dataAcquisitionServiceBaseUrl(), 
				config.dataServiceInterval(), config.lowerSocBounds(), config.upperSocBounds(), 
				config.maxPowerChangePerCycle(), config.maxChargePower(), config.maxDischargePower(),
				config.chargingEfficiencyKeys(), config.chargingEfficiencyValues(),
				config.dischargingEfficiencyKeys(), config.dischargingEfficiencyValues(),
				config.batteryChargingEfficiency(), config.batteryDischargingEfficiency());
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	/**
	 * Calculate SoC state based on current SoC value using internal state machine
	 */
	private SocState calculateSocState(ManagedSymmetricEss ess) {
		Integer soc = ess.getSoc().orElse(50); // Default to 50% if undefined
		socStateMachine.calculateSoCState(soc);
		return socStateMachine.getSoCState();
	}

	/**
	 * Calculate efficiency based on current power level
	 */
	private double calculateEfficiency(int power) {
		if (power == 0) {
			return 1.0;
		}
		
		// Calculate normalized power level (0-1)
		double normalizedPower;
		if (power < 0) {
			// Charging - normalize against max charge power
			normalizedPower = Math.abs(power) / Math.abs(maxChargePower);
		} else {
			// Discharging - normalize against max discharge power
			normalizedPower = power / (double) maxDischargePower;
		}
		
		// Ensure normalized power is within [0,1]
		normalizedPower = Math.max(0, Math.min(1, normalizedPower));
		
		// Get efficiency from lookup table and apply battery efficiency
		if (power < 0) {
			// Charging
			return chargingEfficiencyTable.getEfficiency(normalizedPower) * batteryChargingEfficiency;
		} else {
			// Discharging
			return dischargingEfficiencyTable.getEfficiency(normalizedPower) * batteryDischargingEfficiency;
		}
	}

	/**
	 * Calculate power with efficiency applied
	 */
	private int calculatePowerWithEfficiency(int power) {
		if (power == 0) {
			return 0;
		}
		
		double efficiency = calculateEfficiency(power);
		
		if (power < 0) {
			// Charging - power is reduced by efficiency
			return (int) (power * efficiency);
		} else {
			// Discharging - power is increased to account for losses
			return (int) (power * (1.0 / efficiency));
		}
	}

	/**
	 * Calculate inefficiency power loss
	 */
	private int calculateInefficiencyLoss(int power) {
		if (power == 0) {
			return 0;
		}
		
		int powerWithEfficiency = calculatePowerWithEfficiency(power);
		return Math.abs(power - powerWithEfficiency);
	}

	/**
	 * Filter power to implement ramping/smoothing
	 */
	private int filterPower(int targetPower) {
		int powerDifference = targetPower - lastPower;
		
		if (Math.abs(powerDifference) <= maxPowerChangePerCycle) {
			// Within ramp rate limits - use target power
			lastPower = targetPower;
			return targetPower;
		} else {
			// Apply ramp rate limiting
			if (powerDifference > 0) {
				// Ramping up (more positive/less negative)
				lastPower += maxPowerChangePerCycle;
			} else {
				// Ramping down (more negative/less positive)
				lastPower -= maxPowerChangePerCycle;
			}
			return lastPower;
		}
	}

	/**
	 * Calculate power distribution for dual-battery mode during charging
	 */
	private int[] calculateDualBatteryChargePower(int totalChargePower, SocState mainSocState, SocState supportSocState) {
		double mainPowerPercentage = CHARGE_TABLE[supportSocState.ordinal()][mainSocState.ordinal()];
		int mainPower = (int) (totalChargePower * mainPowerPercentage);
		int supportPower = totalChargePower - mainPower;
		return new int[]{mainPower, supportPower};
	}

	/**
	 * Calculate power distribution for dual-battery mode during discharging
	 */
	private int[] calculateDualBatteryDischargePower(int totalDischargePower, SocState mainSocState, SocState supportSocState) {
		double mainPowerPercentage = DISCHARGE_TABLE[supportSocState.ordinal()][mainSocState.ordinal()];
		int mainPower = (int) (totalDischargePower * mainPowerPercentage);
		int supportPower = totalDischargePower - mainPower;
		return new int[]{mainPower, supportPower};
	}

	/**
	 * Handle ESS in RED state by conserving the low SoC battery
	 */
	private void conserveRed(ManagedSymmetricEss activeEss, ManagedSymmetricEss conservedEss, SocState activeSocState)
			throws OpenemsNamedException {
		int conservedEssPower = conservedEss.getAllowedDischargePower().orElse(0);
		int activeEssPower = activeEss.getAllowedDischargePower().orElse(0);
		int consumption = sum.getConsumptionActivePower().orElse(0);
		int production = sum.getProductionActivePower().orElse(0);
		int essPower = consumption - production - maxGridPower;

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
			activeEssPower = filterPowerForBattery(essPower, "active");
			conservedEssPower = essPower - activeEssPower;
		} else {
			// Demand can not be met with current grid limit + production + both ess.
			// For the future some way to shed load would have to be implemented here.
			activeEssPower = filterPowerForBattery(essPower, "active");
			conservedEssPower = essPower - activeEssPower;
		}

		activeEssPower = filterPowerForBattery(activeEssPower, "active");
		conservedEssPower = filterPowerForBattery(conservedEssPower, "conserved");

		int remainder = essPower - activeEssPower - conservedEssPower;

		if(conservedEssPower <= 0 && (consumption - conservedEssPower < production)) { 
			// if conservedEss & consumption is charged solely on production power, reassign remainder.
			activeEssPower = filterPowerForBattery(activeEssPower + remainder, "active");
		}

		if (consumptionExceedsAvailablePower(consumption, maxGridPower, production, activeEssPower, conservedEssPower)) {
			// In these case some kind of load shedding should happen, as load can not be served by available power sources.
			// TODO Load shedding. Handling of this case. For now just let it happen.
			logInfo(log,String.format("%s Consumption of %d W could not be met with a limited grid " +
							"with a total available Power of %d W. With a grid limit of %d W, %d W from production and %d W from ActiveESS and %d W from ConservedESS. Load shedding required for future.",
					Instant.now(componentManager.getClock()), consumption, maxGridPower + production + conservedEssPower + activeEssPower, maxGridPower, production, activeEssPower, conservedEssPower));
		}

		conservedEss.setActivePowerEquals(conservedEssPower);
		activeEss.setActivePowerEquals(activeEssPower);
		conservedEss.setReactivePowerEquals(0);
		activeEss.setReactivePowerEquals(0);
	}

	/**
	 * Calculate charge power distribution between main and support ESS
	 */
	private double chargePowerSplit(ManagedSymmetricEss mainEss, ManagedSymmetricEss supportEss) {
		SocState mainEssSocState = calculateSocState(mainEss);
		SocState supportSocState = calculateSocState(supportEss);
		return getPowerSplitCharging(mainEssSocState, supportSocState);
	}

	/**
	 * Calculate discharge power distribution between main and support ESS
	 */
	private double dischargePowerSplit(ManagedSymmetricEss mainEss, ManagedSymmetricEss supportEss, int essPower) {
		double powerSplit = 1;
		SocState mainSocState = calculateSocState(mainEss);
		SocState supportSocState = calculateSocState(supportEss);
		
		if(essPower >= netPowerThreshold * mainEss.getMaxApparentPower().orElse(0)
				|| !mainSocState.equals(SocState.GREEN)) {
			powerSplit = getPowerSplitDischarging(mainSocState, supportSocState);
		}
		return powerSplit;
	}

	/**
	 * Power split calculation methods for charge and discharge operations
	 */
	private static double getPowerSplitCharging(SocState mainSocState, SocState supportSocState) {
		if(mainSocState.isUndefined() || supportSocState.isUndefined()) {
			return 1;
		}
		return CHARGE_TABLE[supportSocState.ordinal()][mainSocState.ordinal()];
	}

	private static double getPowerSplitDischarging(SocState mainSocState, SocState supportSocState) {
		if(mainSocState.isUndefined() || supportSocState.isUndefined()) {
			return 1;
		}
		return DISCHARGE_TABLE[supportSocState.ordinal()][mainSocState.ordinal()];
	}

	/**
	 * Power filtering for specific battery
	 */
	private int filterPowerForBattery(int targetPower, String batteryType) {
		// Use the same filtering logic as the main filterPower method
		return filterPower(targetPower);
	}

	@Override
	public void run() throws OpenemsNamedException {
		if (enableDualBatteryMode) {
			runDualBatteryMode();
		} else {
			runSingleBatteryMode();
		}
	}

	/**
	 * Run method for single battery mode using main battery
	 */
	private void runSingleBatteryMode() throws OpenemsNamedException {
		// Get the main battery ESS (primary battery in single mode)
		ManagedSymmetricEss mainEss = componentManager.getComponent(mainId);
		
		// Calculate SoC state internally
		SocState mainSocState = calculateSocState(mainEss);
		
		// Get grid mode from the ESS
		GridMode gridMode = mainEss.getGridMode();

		final int consumption = sum.getConsumptionActivePower().orElse(0);
		final int production = sum.getProductionActivePower().orElse(0);
		int totalStoredEnergy = this.getTotalStoredEnergy(mainEss);
		int essPower = consumption - production;
		
		switch(gridMode) {
		case UNDEFINED:
			this.logWarn(this.log, "Grid-Mode is [UNDEFINED]");
		case ON_GRID:
			break;
		case OFF_GRID:
		default:
			throw new IllegalStateException(String.format("Unknown state %s for grid mode of ess.", gridMode));
		}

		// Logic for single battery system
		boolean isRedState = mainSocState == SocState.RED;
		boolean isBelowMinEnergy = enableMinimumEnergyFunction && (defaultMinimumEnergy >= totalStoredEnergy);
		boolean shouldForceCharge = shouldChargeNow();
		
		if(isRedState || isBelowMinEnergy || shouldForceCharge) {
			// Use grid to meet demand/ charge the ess as well. In case of depleted ESS or minimum energy not met.
			essPower = consumption - (production + maxGridPower);
			
			// Log charging trigger conditions
			StringBuilder chargingReason = new StringBuilder("SINGLE BATTERY CHARGING TRIGGERED: ");
			if (isRedState) {
				chargingReason.append("RED_SOC_STATE (SoC: ").append(mainEss.getSoc().orElse(0)).append("%) ");
			}
			if (isBelowMinEnergy) {
				chargingReason.append("MIN_ENERGY_THRESHOLD (stored: ").append(totalStoredEnergy)
					.append("Wh < min: ").append(defaultMinimumEnergy).append("Wh) ");
			}
			if (shouldForceCharge) {
				chargingReason.append("EXTERNAL_CHARGE_DECISION ");
			}
			chargingReason.append("| Grid Power: ").append(maxGridPower).append("W");
			
			this.logInfo(this.log, chargingReason.toString());
		}

		// Apply power filtering/ramping
		int mainEssPower = filterPower(essPower);

		if (consumptionExceedsAvailablePower(consumption, maxGridPower, production, mainEssPower, 0)) {
			// TODO Load shedding. Handling of this case. For now just let it happen.
			logInfo(log,String.format("%s Consumption of %d W could not be met with a limited grid " +
					"with a total available Power of %d W. With a grid limit of %d W, %d W from production and %d W from Main. Load shedding required for future.",
					Instant.now(componentManager.getClock()), consumption, maxGridPower + production + mainEssPower, maxGridPower, production, mainEssPower));
		}

		// Set power for main battery
		mainEss.setActivePowerEquals(mainEssPower);
		mainEss.setReactivePowerEquals(0);

		logMainEssData(mainEss, mainEssPower);
	}

	/**
	 * Run method for dual battery mode
	 */
	private void runDualBatteryMode() throws OpenemsNamedException {
		// Get both battery ESS components
		ManagedSymmetricEss mainEss = componentManager.getComponent(mainId);
		ManagedSymmetricEss supportEss = componentManager.getComponent(supportId);
		
		// Calculate SoC states internally for both batteries
		SocState mainSocState = calculateSocState(mainEss);
		SocState supportSocState = calculateSocState(supportEss);
		
		// Get grid mode from main ESS
		GridMode gridMode = mainEss.getGridMode();

		final int consumption = sum.getConsumptionActivePower().orElse(0);
		final int production = sum.getProductionActivePower().orElse(0);
		int totalStoredEnergy = this.getTotalStoredEnergy(mainEss, supportEss);
		int essPower = consumption - production;
		
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

		// Dual battery logic for charge/discharge decisions
		if(mainSocState == SocState.RED && supportSocState == SocState.RED
				|| enableMinimumEnergyFunction && defaultMinimumEnergy >= totalStoredEnergy || shouldChargeNow()) {
			// Use grid to meet demand/ charge the ess as well. In case of depleted ESS or minimum energy not met.
			essPower = consumption - (production + maxGridPower);
			
			// Log charging trigger conditions
			StringBuilder chargingReason = new StringBuilder("DUAL BATTERY CHARGING TRIGGERED: ");
			if (mainSocState == SocState.RED && supportSocState == SocState.RED) {
				chargingReason.append("BOTH_RED_SOC_STATE (Main: ").append(mainEss.getSoc().orElse(0))
					.append("%, Support: ").append(supportEss.getSoc().orElse(0)).append("%) ");
			}
			if (enableMinimumEnergyFunction && defaultMinimumEnergy >= totalStoredEnergy) {
				chargingReason.append("MIN_ENERGY_THRESHOLD (stored: ").append(totalStoredEnergy)
					.append("Wh < min: ").append(defaultMinimumEnergy).append("Wh) ");
			}
			if (shouldChargeNow()) {
				chargingReason.append("EXTERNAL_CHARGE_DECISION ");
			}
			chargingReason.append("| Grid Power: ").append(maxGridPower).append("W");
			
			this.logInfo(this.log, chargingReason.toString());
		} else if (mainSocState == SocState.RED) { 
			// Special case: main ESS in RED - preserve it, use support ESS
			conserveRed(supportEss, mainEss, supportSocState);
			return;
		} else if (supportSocState == SocState.RED) {
			// Special case: support ESS in RED - preserve it, use main ESS
			conserveRed(mainEss, supportEss, mainSocState);
			return;
		}

		// Power splitting logic
		if(essPower <= 0) { // charging.
			powerSplit = chargePowerSplit(mainEss, supportEss);
		} else { // Discharging
			powerSplit = dischargePowerSplit(mainEss, supportEss, essPower);
		}

		// Apply power filtering for smooth operation
		int mainEssPower = filterPowerForBattery((int) (powerSplit * essPower), "main");
		int supportEssPower = filterPowerForBattery(essPower - mainEssPower, "support");

		int remainder = essPower - mainEssPower - supportEssPower;
		mainEssPower = filterPowerForBattery(mainEssPower + remainder, "main");

		if (consumptionExceedsAvailablePower(consumption, maxGridPower, production, mainEssPower, supportEssPower)) {
			// TODO Load shedding. Handling of this case. For now just let it happen.
			logInfo(log,String.format("%s Consumption of %d W could not be met with a limited grid " +
					"with a total available Power of %d W. With a grid limit of %d W, %d W from production and %d W from Main and %d W from Support. Load shedding required for future.",
					Instant.now(componentManager.getClock()), consumption, maxGridPower + production + mainEssPower + supportEssPower, maxGridPower, production, mainEssPower, supportEssPower));
		}

		// Set power for both batteries
		mainEss.setActivePowerEquals(mainEssPower);
		supportEss.setActivePowerEquals(supportEssPower);
		mainEss.setReactivePowerEquals(0);
		supportEss.setReactivePowerEquals(0);

		logDualBatteryEssData(mainEss, supportEss, mainEssPower, supportEssPower);
	}

	private boolean consumptionExceedsAvailablePower(int consumption, int gridLimit, int production, int firstEssPower, int secondEssPower) {
		return (gridLimit + production) < consumption - firstEssPower - secondEssPower;
	}

	/**
	 * Get total stored energy for single battery mode (main battery only)
	 */
	private int getTotalStoredEnergy(ManagedSymmetricEss mainEss) {
		Integer capacity = mainEss.getCapacity().orElse(null);
		Integer soc = mainEss.getSoc().orElse(null);
		
		if (capacity == null || soc == null) {
			// If capacity or SoC is not available, only log warning if minimum energy function is enabled
			if (enableMinimumEnergyFunction) {
				this.logWarn(this.log, "Main ESS capacity (" + capacity + " Wh) or SoC (" + soc + " %) is not available - skipping minimum energy check");
			}
			return Integer.MAX_VALUE; // Return high value to disable minimum energy check
		}
		
		int mainEssStoredEnergy = (capacity * soc);
		return mainEssStoredEnergy / 100;
	}

	/**
	 * Get total stored energy for dual battery mode
	 */
	private int getTotalStoredEnergy(ManagedSymmetricEss mainEss, ManagedSymmetricEss supportEss) {
		Integer mainCapacity = mainEss.getCapacity().orElse(null);
		Integer mainSoc = mainEss.getSoc().orElse(null);
		Integer supportCapacity = supportEss.getCapacity().orElse(null);
		Integer supportSoc = supportEss.getSoc().orElse(null);
		
		if (mainCapacity == null || mainSoc == null || supportCapacity == null || supportSoc == null) {
			// If any capacity or SoC is not available, only log warning if minimum energy function is enabled
			if (enableMinimumEnergyFunction) {
				this.logWarn(this.log, "ESS capacities or SoCs not available - skipping minimum energy check");
			}
			return Integer.MAX_VALUE; // Return high value to disable minimum energy check
		}
		
		int mainEssStoredEnergy = (mainCapacity * mainSoc) / 100;
		int supportEssStoredEnergy = (supportCapacity * supportSoc) / 100;
		return mainEssStoredEnergy + supportEssStoredEnergy;
	}

	private int shouldChargeCounter = 0;
	private boolean cachedShouldCharge = false;

	private boolean shouldChargeNow() {
		shouldChargeCounter++;
	    if (shouldChargeCounter % dataServiceInterval != 0) {
	        // Skip sending to Flask server this cycle
	    	return cachedShouldCharge;
	    }
	    
	    try {
	        // Get the simulation time
	        Instant simulationTime = Instant.now(this.componentManager.getClock());
	        String timestampParam = URLEncoder.encode(simulationTime.toString(), "UTF-8");

	        // Construct the URL with the timestamp parameter using dataAcquisitionServiceBaseUrl
	        URL url = new URL(this.dataAcquisitionServiceBaseUrl + "should_charge_now?timestamp=" + timestampParam);
	        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	        conn.setRequestMethod("GET");
	        conn.setRequestProperty("Accept", "text/plain");

	        int responseCode = conn.getResponseCode();
	        if (responseCode != HttpURLConnection.HTTP_OK) {
	            this.logWarn(this.log, "Failed to get shouldChargeNow: HTTP error code " + responseCode);
	            return false; // Default to not forcing charging
	        }

	        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
	        StringBuilder responseBuilder = new StringBuilder();
	        String line;
	        while ((line = in.readLine()) != null) {
	            responseBuilder.append(line);
	        }
	        in.close();
	        conn.disconnect();

	        String response = responseBuilder.toString().trim();
	        boolean parsedResult = false;

	        // JSON parsing assuming the format is: {"should_charge":true}
	        if (response.startsWith("{") && response.endsWith("}")) {
	            int keyIndex = response.indexOf("\"should_charge\"");
	            if (keyIndex != -1) {
	                int colonIndex = response.indexOf(":", keyIndex);
	                if (colonIndex != -1) {
	                    String valuePart = response.substring(colonIndex + 1).trim();
	                    // Remove trailing } if it's still there
	                    if (valuePart.endsWith("}")) {
	                        valuePart = valuePart.substring(0, valuePart.length() - 1).trim();
	                    }
	                    // Parse boolean value
	                    parsedResult = valuePart.equalsIgnoreCase("true");
	                }
	            }
	        }

	        cachedShouldCharge = parsedResult;
	        this.logInfo(this.log, "Response shouldChargeNow: " + response + " (bool: " + cachedShouldCharge + ")");
		return cachedShouldCharge;
	    } catch (Exception e) {
	        this.logWarn(this.log, "Failed to connect to shouldChargeNow service: " + e.getMessage());
	        return false; // Default to not forcing charging in case of error
	    }
	}

	/**
	 * Log data for single battery mode (main battery)
	 */
	private void logMainEssData(ManagedSymmetricEss mainEss, int actualPower) {
		flaskSendCounter++;
		if (flaskSendCounter % dataServiceInterval != 0) {
			// Skip sending to Flask server this cycle
			return;
		}

		try {
			Integer soc = mainEss.getSoc().orElse(0);
			Integer activePower = mainEss.getActivePower().orElse(0);
			Integer allowedChargePower = mainEss.getAllowedChargePower().orElse(0);
			Integer allowedDischargePower = mainEss.getAllowedDischargePower().orElse(0);
			
			// Calculate efficiency and inefficiency loss
			double efficiency = calculateEfficiency(actualPower);
			Integer inefficiencyLossPower = calculateInefficiencyLoss(actualPower);
			
			Instant simulationTime = Instant.now(this.componentManager.getClock());

			URL url = new URL(this.dataAcquisitionServiceBaseUrl + "logdata");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");

			String jsonInputString = "{"
					+ "\"timestamp\":\"" + simulationTime.toString() + "\","
					+ "\"singleBatteryMode\":true,"
					+ "\"soc\":" + soc + ","
					+ "\"activePower\":" + activePower + ","
					+ "\"allowedChargePower\":" + allowedChargePower + ","
					+ "\"allowedDischargePower\":" + allowedDischargePower + ","
					+ "\"efficiency\":" + efficiency + ","
					+ "\"inefficiencyLossPower\":" + inefficiencyLossPower
					+ "}";

			OutputStream os = conn.getOutputStream();
			byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
			os.flush();
			os.close();

			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				this.logWarn(this.log, "Failed sending main ESS data: HTTP error code : " + conn.getResponseCode());
			}
			conn.disconnect();
		} catch (Exception e) {
			this.logWarn(this.log, "Error sending main ESS data to server: " + e.getMessage());
		}
	}

	private void logSupportEssData(ManagedSymmetricEss supportEss, int actualPower) {
		flaskSendCounter++;
		if (flaskSendCounter % dataServiceInterval != 0) {
			// Skip sending to Flask server this cycle
			return;
		}

		try {
			Integer soc = supportEss.getSoc().orElse(0);
			Integer activePower = supportEss.getActivePower().orElse(0);
			Integer allowedChargePower = supportEss.getAllowedChargePower().orElse(0);
			Integer allowedDischargePower = supportEss.getAllowedDischargePower().orElse(0);
			
			// Calculate efficiency and inefficiency loss
			double efficiency = calculateEfficiency(actualPower);
			Integer inefficiencyLossPower = calculateInefficiencyLoss(actualPower);
			
			Instant simulationTime = Instant.now(this.componentManager.getClock());

			URL url = new URL(this.dataAcquisitionServiceBaseUrl + "logdata");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");

			String jsonInputString = "{"
					+ "\"timestamp\":\"" + simulationTime.toString() + "\","
					+ "\"soc\":" + soc + ","
					+ "\"activePower\":" + activePower + ","
					+ "\"allowedChargePower\":" + allowedChargePower + ","
					+ "\"allowedDischargePower\":" + allowedDischargePower + ","
					+ "\"efficiency\":" + efficiency + ","
					+ "\"inefficiencyLossPower\":" + inefficiencyLossPower
					+ "}";

			OutputStream os = conn.getOutputStream();
			byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
			os.flush();
			os.close();

			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				this.logWarn(this.log, "Failed sending support ESS data: HTTP error code : " + conn.getResponseCode());
			}
			conn.disconnect();
		} catch (Exception e) {
			this.logWarn(this.log, "Error sending support ESS data to server: " + e.getMessage());
		}
	}

	/**
	 * Log data for dual battery mode
	 */
	private void logDualBatteryEssData(ManagedSymmetricEss mainEss, ManagedSymmetricEss supportEss, int mainActualPower, int supportActualPower) {
		flaskSendCounter++;
		if (flaskSendCounter % dataServiceInterval != 0) {
			// Skip sending to Flask server this cycle
			return;
		}

		try {
			// Main ESS data
			Integer mainSoc = mainEss.getSoc().orElse(0);
			Integer mainActivePower = mainEss.getActivePower().orElse(0);
			Integer mainAllowedChargePower = mainEss.getAllowedChargePower().orElse(0);
			Integer mainAllowedDischargePower = mainEss.getAllowedDischargePower().orElse(0);
			
			// Support ESS data
			Integer supportSoc = supportEss.getSoc().orElse(0);
			Integer supportActivePower = supportEss.getActivePower().orElse(0);
			Integer supportAllowedChargePower = supportEss.getAllowedChargePower().orElse(0);
			Integer supportAllowedDischargePower = supportEss.getAllowedDischargePower().orElse(0);
			
			// Calculate efficiencies
			double mainEfficiency = calculateEfficiency(mainActualPower);
			double supportEfficiency = calculateEfficiency(supportActualPower);
			Integer mainInefficiencyLossPower = calculateInefficiencyLoss(mainActualPower);
			Integer supportInefficiencyLossPower = calculateInefficiencyLoss(supportActualPower);
			
			Instant simulationTime = Instant.now(this.componentManager.getClock());

			URL url = new URL(this.dataAcquisitionServiceBaseUrl + "logdata");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");

			String jsonInputString = "{"
					+ "\"timestamp\":\"" + simulationTime.toString() + "\","
					+ "\"dualBatteryMode\":true,"
					+ "\"mainSoc\":" + mainSoc + ","
					+ "\"mainActivePower\":" + mainActivePower + ","
					+ "\"mainAllowedChargePower\":" + mainAllowedChargePower + ","
					+ "\"mainAllowedDischargePower\":" + mainAllowedDischargePower + ","
					+ "\"mainEfficiency\":" + mainEfficiency + ","
					+ "\"mainInefficiencyLossPower\":" + mainInefficiencyLossPower + ","
					+ "\"supportSoc\":" + supportSoc + ","
					+ "\"supportActivePower\":" + supportActivePower + ","
					+ "\"supportAllowedChargePower\":" + supportAllowedChargePower + ","
					+ "\"supportAllowedDischargePower\":" + supportAllowedDischargePower + ","
					+ "\"supportEfficiency\":" + supportEfficiency + ","
					+ "\"supportInefficiencyLossPower\":" + supportInefficiencyLossPower
					+ "}";

			OutputStream os = conn.getOutputStream();
			byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
			os.flush();
			os.close();

			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				this.logWarn(this.log, "Failed sending dual battery ESS data: HTTP error code : " + conn.getResponseCode());
			}
			conn.disconnect();
		} catch (Exception e) {
			this.logWarn(this.log, "Error sending dual battery ESS data to server: " + e.getMessage());
		}
	}
}

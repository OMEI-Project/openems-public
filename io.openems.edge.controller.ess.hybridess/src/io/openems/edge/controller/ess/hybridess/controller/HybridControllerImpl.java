package io.openems.edge.controller.ess.hybridess.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Optional;

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

	private String mainId;
	private String supportId;

	private String dataAcquisitionServiceBaseUrl;
	
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

	private int flaskSendCounter = 0;

	@Reference
	private ComponentManager componentManager;
	
	@Reference 
	private Sum sum;

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
		internalActivate(defaultMinimumEnergy, maxGridPower, mainId, supportId, dataAcquisitionServiceBaseUrl);
	}

	public HybridControllerImpl(){
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				HybridController.ChannelId.values() //
		);
	}

	private void internalActivate(int defaultMinimumEnergy, int maxGridPower, String mainId, String supportId,
							 String dataAcquisitionServiceBaseUrl) {
		this.defaultMinimumEnergy = defaultMinimumEnergy;
		this.maxGridPower = maxGridPower;
		this.mainId = mainId;
		this.supportId=supportId;
		this.dataAcquisitionServiceBaseUrl = dataAcquisitionServiceBaseUrl;
	}
	
	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		internalActivate(config.defaultMinimumEnergy(),
				config.maxGridPower(), config.mainId(), config.supportId(), config.dataAcquisitionServiceBaseUrl());
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


		if(mainSocState == SocState.RED && supportSocState == SocState.RED
				|| defaultMinimumEnergy >= totalStoredEnergy || shouldChargeNow()) {

			// Use grid to meet demand/ charge the ess as well. In case of depleted ESS or minimum energy not met.
			essPower = consumption - (production + maxGridPower);
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

		if (consumptionExceedsAvailablePower(consumption, maxGridPower, production, mainEssPower, supportEssPower)) {

			// TODO Load shedding. Handling of this case. For now just let it happen.
			logInfo(log,String.format("%s Consumption of %d W could not be met with a limited grid " +
					"with a total available Power of %d W. With a grid limit of %d W, %d W from production and %d W from Main and %d W from Support. Load shedding required for future.",
					Instant.now(componentManager.getClock()), consumption, maxGridPower + production + mainEssPower + supportEssPower, maxGridPower, production, mainEssPower, supportEssPower));
		}

		mainEss.setActivePowerEquals(mainEssPower);
		supportEss.setActivePowerEquals(supportEssPower);

		mainEss.setReactivePowerEquals(0);
		supportEss.setReactivePowerEquals(0);

		logSupportEssData(supportEss);
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
			activeEssPower = activeEss.filterPower(essPower);
			conservedEssPower = essPower - activeEssPower;
		} else {

			// Demand can not be met with current grid limit + production + both ess.
			// For the future some way to shed load would have to be implemented here.
			activeEssPower = activeEss.filterPower(essPower);
			conservedEssPower = essPower - activeEssPower;
		}

		activeEssPower = activeEss.filterPower(activeEssPower);
		conservedEssPower = conservedEss.filterPower(conservedEssPower);

		int remainder = essPower - activeEssPower - conservedEssPower;

		if(conservedEssPower <= 0 && (consumption - conservedEssPower < production)) { // if conservedEss & consumption is charged solely on production power, reassign remainder.
			activeEssPower = activeEss.filterPower(activeEssPower + remainder);
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

	private boolean consumptionExceedsAvailablePower(int consumption, int gridLimit, int production, int firstEssPower, int secondEssPower) {
		return (gridLimit + production) < consumption - firstEssPower - secondEssPower;
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
		if(mainSocState.isUndefined() || supportSocState.isUndefined()) {
			//throw new IllegalArgumentException("State undefined.");
			return 1;
		}
		return CHARGE_TABLE[supportSocState.getValue()][mainSocState.getValue()];
	}

	private static double getPowerSplitDischarging(SocState mainSocState, SocState supportSocState) {
		if(mainSocState.isUndefined() || supportSocState.isUndefined()) {
			//throw new IllegalArgumentException("State undefined.");
			return 1;
		}
		return DISCHARGE_TABLE[supportSocState.getValue()][mainSocState.getValue()];
	}
	
	private int shouldChargeCounter = 0;
	private boolean cachedShouldCharge = false;
	
	private boolean shouldChargeNow() {
		shouldChargeCounter++;
	    if (shouldChargeCounter % 60 != 0) {
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
	        String responseLine = in.readLine();
	        in.close();
	        conn.disconnect();

	        // The response should be 'true' or 'false'
	        cachedShouldCharge = "true".equalsIgnoreCase(responseLine.trim());
	        return cachedShouldCharge;
	    } catch (Exception e) {	        
	        return false; // Default to not forcing charging in case of error
	    }
	}

	private void logSupportEssData(ManagedSymmetricEssHybrid supportEss) {
		flaskSendCounter++;
		if (flaskSendCounter % 60 != 0) {
			// Skip sending to Flask server this cycle
			return;
		}

		try {
			Integer soc = supportEss.getSoc().orElse(0);
			Integer activePower = supportEss.getActivePower().orElse(0);
			Integer allowedChargePower = supportEss.getAllowedChargePower().orElse(0);
			Integer allowedDischargePower = supportEss.getAllowedDischargePower().orElse(0);
			// Efficiency might not be directly available or meaningful in the same way as the combined EssSymmetricHybrid
			// For now, let's send a placeholder or consider if it's needed.
			// double efficiency = supportEss.getEfficiencyByPower(); // This method doesn't exist on ManagedSymmetricEssHybrid
			double efficiency = 1.0; // Placeholder
			// Integer inefficiencyLossPower = supportEss.getInefficiencyLossPower(); // This method doesn't exist
			Integer inefficiencyLossPower = 0; // Placeholder
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
}

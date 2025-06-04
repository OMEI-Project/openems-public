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
	 * NOTE: Preserved for dual-battery mode reactivation
	 */
	private final static double[][] CHARGE_TABLE =
			{{0.5, 0.3, 0},
			{0.7, 0.7, 0.2},
			{1.0, 0.8, 0.5}};

	/**
	 * Distribution of required discharge power between the two ESSs based
	 * on the SoC.
	 * ChargeTable[SocStateSupport][SocStateMain] = % of required power drawn from main.
	 * NOTE: Preserved for dual-battery mode reactivation
	 */
	private final static double[][] DISCHARGE_TABLE =
			{{0.5, 0.7, 1.0},
			{0.3, 0.7, 0.7},
			{0, 0.3, 0.7}};

	private final Logger log = LoggerFactory.getLogger(HybridControllerImpl.class);

	// Commented out for single battery mode - can be easily reactivated
	// private String mainId;
	private String supportId;

	private String dataAcquisitionServiceBaseUrl;
	
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

	// Percentage of mainEss's maximum power output, that mainEss should supply alone as netpower.
	// NOTE: Preserved for dual-battery mode reactivation
	private final double netPowerThreshold = 0.8;

	private int flaskSendCounter = 0;

	@Reference
	private ComponentManager componentManager;
	
	@Reference 
	private Sum sum;

	// Constructor for testing with dual battery (commented out main battery references)
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
		internalActivate(defaultMinimumEnergy, maxGridPower, /* mainId, */ supportId, dataAcquisitionServiceBaseUrl, 10);
	}

	public HybridControllerImpl(){
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				HybridController.ChannelId.values() //
		);
	}

	// Modified to work with single battery
	private void internalActivate(int defaultMinimumEnergy, int maxGridPower, /* String mainId, */ String supportId,
							 String dataAcquisitionServiceBaseUrl, int dataServiceInterval) {
		this.defaultMinimumEnergy = defaultMinimumEnergy;
		this.maxGridPower = maxGridPower;
		// this.mainId = mainId;  // Commented out for single battery mode
		this.supportId=supportId;
		this.dataAcquisitionServiceBaseUrl = dataAcquisitionServiceBaseUrl;
		this.dataServiceInterval = dataServiceInterval;
	}
	
	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		internalActivate(config.defaultMinimumEnergy(),
				config.maxGridPower(), /* config.mainId(), */ config.supportId(), config.dataAcquisitionServiceBaseUrl(), config.dataServiceInterval());
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}


	@Override
	public void run() throws OpenemsNamedException {
		// Single battery mode - only use supportEss
		// ManagedSymmetricEssHybrid mainEss = componentManager.getComponent(mainId);  // Commented out
		ManagedSymmetricEssHybrid supportEss  = componentManager.getComponent(supportId);
		
		// For single battery mode, use only the supportEss grid mode
		GridMode gridMode = supportEss.getGridMode(); // Simplified for single battery
		// SocState mainSocState = mainEss.getSocState();  // Commented out
		SocState supportSocState = supportEss.getSocState();

		final int consumption = sum.getConsumptionActivePower().orElse(0);
		final int production = sum.getProductionActivePower().orElse(0);
		int totalStoredEnergy = this.getTotalStoredEnergy(/* mainEss, */ supportEss);
		int essPower = consumption - production;
		
		// Simplified power split logic for single battery
		// double powerSplit = 1.0;  // Not needed for single battery
		switch(gridMode) {
		case UNDEFINED:
			this.logWarn(this.log, "Grid-Mode is [UNDEFINED]");
		case ON_GRID:
			break;
		case OFF_GRID:
		default:
			throw new IllegalStateException(String.format("Unknown state %s for grid mode of ess.", gridMode));
		}

		// Simplified logic for single battery system
		if(supportSocState == SocState.RED || defaultMinimumEnergy >= totalStoredEnergy || shouldChargeNow()) {
			// Use grid to meet demand/ charge the ess as well. In case of depleted ESS or minimum energy not met.
			essPower = consumption - (production + maxGridPower);
		}

		// Single battery logic - no power splitting needed
		int supportEssPower = supportEss.filterPower(essPower);

		if (consumptionExceedsAvailablePower(consumption, maxGridPower, production, 0, supportEssPower)) {
			// TODO Load shedding. Handling of this case. For now just let it happen.
			logInfo(log,String.format("%s Consumption of %d W could not be met with a limited grid " +
					"with a total available Power of %d W. With a grid limit of %d W, %d W from production and %d W from Support. Load shedding required for future.",
					Instant.now(componentManager.getClock()), consumption, maxGridPower + production + supportEssPower, maxGridPower, production, supportEssPower));
		}

		// Set power for single battery
		supportEss.setActivePowerEquals(supportEssPower);
		supportEss.setReactivePowerEquals(0);

		logSupportEssData(supportEss);
	}

	// Commented out - only needed for dual battery mode
	/*
	private void conserveRed(ManagedSymmetricEssHybrid activeEss, ManagedSymmetricEssHybrid conservedEss, SocState activeSocState)
			throws OpenemsNamedException {
		// ... existing code preserved for reactivation
	}
	*/

	private boolean consumptionExceedsAvailablePower(int consumption, int gridLimit, int production, int firstEssPower, int secondEssPower) {
		return (gridLimit + production) < consumption - firstEssPower - secondEssPower;
	}

	// Modified for single battery
	private int getTotalStoredEnergy(/* ManagedSymmetricEssHybrid mainEss, */ ManagedSymmetricEssHybrid supportEss) throws InvalidValueException {
		// int mainEssStoredEnergy = (mainEss.getCapacity().getOrError() * (mainEss.getSoc().getOrError()));  // Commented out
		int supportEssStoredEnergy = (supportEss.getCapacity().getOrError() * (supportEss.getSoc().getOrError()));
		return supportEssStoredEnergy / 100;  // Simplified for single battery
	}

	// Commented out - not needed for single battery mode
	/*
	private double chargePowerSplit(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss) throws InvalidValueException {
		// ... existing code preserved for reactivation
	}

	private double dischargePowerSplit(ManagedSymmetricEssHybrid mainEss, ManagedSymmetricEssHybrid supportEss, int essPower) throws InvalidValueException {
		// ... existing code preserved for reactivation
	}

	private static double getPowerSplitCharging(SocState mainSocState, SocState supportSocState) {
		// ... existing code preserved for reactivation
	}

	private static double getPowerSplitDischarging(SocState mainSocState, SocState supportSocState) {
		// ... existing code preserved for reactivation
	}
	*/
	
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
	        this.logWarn(this.log, "Response shouldChargeNow: " + response + " (bool: " + cachedShouldCharge + ")");
	        return cachedShouldCharge;
	    } catch (Exception e) {	        
	        return false; // Default to not forcing charging in case of error
	    }
	}

	private void logSupportEssData(ManagedSymmetricEssHybrid supportEss) {
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

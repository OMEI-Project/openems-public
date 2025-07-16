package io.openems.edge.controller.ess.hybridess.controller;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller HybridController", //
		description = "Controller for ESS with internal state management")
public
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlHybridController0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";
	
	@AttributeDefinition(name = "Battery-Ess", description = "ID of Battery-Ess. Primary battery system.")
	String supportId();

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;
	
	@AttributeDefinition(name = "Default Minimum Energy", 
			description = "Minimal total Energy in Wh that should be stored by ESS to ensure EVs can be serviced.", min="0")
	int defaultMinimumEnergy() default 500;
	
	@AttributeDefinition(name ="Maximum Grid Power", description = "Maximum power that can be drawn from grid in W.")
	int maxGridPower() default 200_000;

	@AttributeDefinition(name = "Data Acquisition Service Base URL", description = "Base URL for the external data acquisition service (e.g., http://127.0.0.1:5000/). Specific paths like '/logdata' or '/should_charge_now' will be appended.")
	String dataAcquisitionServiceBaseUrl() default "http://127.0.0.1:5000/";

	@AttributeDefinition(name = "Data Service Communication Interval", 
			description = "Interval in controller cycles between communications with the data acquisition service (for both fetching and posting data).", min="1")
	int dataServiceInterval() default 10;

	@AttributeDefinition(name = "Lower SoC Bounds", 
			description = "Lower SoC bounds for state machine [RED->ORANGE, ORANGE->GREEN] in percent.", min="0", max="100")
	int[] lowerSocBounds() default {10, 20};

	@AttributeDefinition(name = "Upper SoC Bounds", 
			description = "Upper SoC bounds for state machine [RED->ORANGE, ORANGE->GREEN] in percent.", min="0", max="100")
	int[] upperSocBounds() default {85, 95};

	@AttributeDefinition(name = "Max Power Change Per Cycle", 
			description = "Maximum power change per cycle in W for power filtering/ramping.", min="100")
	int maxPowerChangePerCycle() default 1000;

	@AttributeDefinition(name = "Max Charge Power", 
			description = "Maximum charge power in W (positive value, will be converted to negative internally).", min="0")
	int maxChargePower() default 276_000;

	@AttributeDefinition(name = "Max Discharge Power", 
			description = "Maximum discharge power in W.", min="0")
	int maxDischargePower() default 276_000;

	@AttributeDefinition(name = "Charging Efficiency Keys", 
			description = "Keys for charging efficiency lookup table based on power level (0.0 to 1.0). Must have same length as values.")
	double[] chargingEfficiencyKeys() default {0.0, 0.5, 1.0};

	@AttributeDefinition(name = "Charging Efficiency Values", 
			description = "Values for charging efficiency lookup table (0.0 to 1.0). Must have same length as keys.")
	double[] chargingEfficiencyValues() default {0.85, 0.90, 0.85};

	@AttributeDefinition(name = "Discharging Efficiency Keys", 
			description = "Keys for discharging efficiency lookup table based on power level (0.0 to 1.0). Must have same length as values.")
	double[] dischargingEfficiencyKeys() default {0.0, 0.5, 1.0};

	@AttributeDefinition(name = "Discharging Efficiency Values", 
			description = "Values for discharging efficiency lookup table (0.0 to 1.0). Must have same length as keys.")
	double[] dischargingEfficiencyValues() default {0.85, 0.90, 0.85};

	@AttributeDefinition(name = "Battery Charging Efficiency", 
			description = "Additional battery charging efficiency factor (0.0 to 1.0).")
	double batteryChargingEfficiency() default 0.95;

	@AttributeDefinition(name = "Battery Discharging Efficiency", 
			description = "Additional battery discharging efficiency factor (0.0 to 1.0).")
	double batteryDischargingEfficiency() default 0.95;

	String webconsole_configurationFactory_nameHint() default "Controller HybridController [{id}]";

}
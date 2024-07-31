package io.openems.edge.simulator.ess.symmetric.hybrid;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.common.sum.GridMode;

import java.util.Locale;

@ObjectClassDefinition(//
		name = "Simulator EssSymmetric Reacting OMEI", //
		description = "This simulates a 'reacting' symmetric Energy Storage System.")
public @interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ess0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Capacity [Wh]")
	int capacity() default 10000;
	
	@AttributeDefinition(name = "Power Step [W/s]")
	int rampRate() default 20000;
	
	@AttributeDefinition(name = "Response Time [ms]", description = "Time from power requested to first power supplied.")
	long responseTime() default 0;

	@AttributeDefinition(name = "Inactivity Time [ms]", description = "Time spent inactive (power output/ input = 0W) " +
			"after which Response Time has to elapse again.")
	long inactivityTime() default 10_000;

	@AttributeDefinition(name = "Minimum State of Charge [%]")
	int minimumSoc() default 10;

	@AttributeDefinition(name = "Maximum State of Charge [%]")
	int maximumSoc() default 90;

	@AttributeDefinition(name = "Initial State of Charge [%]")
	int initialSoc() default 50;

	@AttributeDefinition(name = "Grid mode")
	GridMode gridMode() default GridMode.ON_GRID;

	String webconsole_configurationFactory_nameHint() default "Simulator EssSymmetric Reacting [{id}]";

	@AttributeDefinition(name ="Allowed Discharge Power", description="Maximum amount of power in [W] this ESS can discharge. Has to be >= 0", min = "0")
	int allowedDischargePower();

	@AttributeDefinition(name="Allowed Charge Power", description="Maximum amount of power in [W] this ESS can be charged with. Has to be <=0", max = "0")
	int allowedChargePower();
	
	@AttributeDefinition(name="Efficiency Keys of Inverter for Charging", description = "Keys for efficiency lookup-table based on {Power/C-Rate?}." +
            " Has to have the same amount of entries as 'Efficiency Values'. key[i] will map to value[i]")
	double[] chargingEfficiencyKeys() default {1.0};
	
	@AttributeDefinition(name = "Efficiency Values of Inverter for Charging", description = "Values for the efficiency lookup-table." +
	            " Has to have the same amount of entries as 'Efficiency Keys'.key[i] will map to value[i]")
	double[] chargingEfficiencyValues() default {1.0};
	
	
	@AttributeDefinition(name="Efficiency Keys of Inverter for Discharging", description = "Keys for efficiency lookup-table based on {Power/C-Rate?}." +
	            " Has to have the same amount of entries as 'Efficiency Values'. key[i] will map to value[i]")
	double[] dischargingEfficiencyKeys() default {1.0};
	
	@AttributeDefinition(name = "Efficiency Values of Inverter for Discharging", description = "Values for the efficiency lookup-table." +
	            " Has to have the same amount of entries as 'Efficiency Keys'.key[i] will map to value[i]")
	double[] dischargingEfficiencyValues() default {1.0};
	
	@AttributeDefinition(name="Efficiency of Battery (Not Including Inverter) for Charging", description = "Efficiency as percentage.")
	double batteryChargingEfficiency() default 1.0;
	
	@AttributeDefinition(name = "Efficiency of Battery (Not Including Inverter) for Discharging", description = "Efficiency as percentage.")
	double batteryDischargingEfficiency() default 1.0;
	
	@AttributeDefinition(name = "Upper SoC Border", description ="Denotes SoC at which the battery will change to higher SoCState")
	int[] higherSocBorder() default {25,50};

	@AttributeDefinition(name = "Lower SoC Border", description ="Denotes SoC at which the battery will change to lower SoCState")
	int[] lowerSocBorder()  default {20,50};
}

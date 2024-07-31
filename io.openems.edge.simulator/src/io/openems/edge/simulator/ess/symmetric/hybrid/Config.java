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
	
	@AttributeDefinition(name="Charging Inverter Efficiency Keys", description = "Keys for efficiency lookup-table based on {Power/C-Rate?}." +
            " Has to have the same amount of entries as 'Efficiency Values'. key[i] will map to value[i]")
	String[] chargingEfficiencyKeys() default {"1.0"};
	
	@AttributeDefinition(name = "Charging Inverter Efficiency Values", description = "Values for the efficiency lookup-table." +
	            " Has to have the same amount of entries as 'Efficiency Keys'.key[i] will map to value[i]")
	String[] chargingEfficiencyValues() default {"1.0"};
	
	
	@AttributeDefinition(name="Discharging Inverter Efficiency Keys", description = "Keys for efficiency lookup-table based on {Power/C-Rate?}." +
	            " Has to have the same amount of entries as 'Efficiency Values'. key[i] will map to value[i]")
	String[] dischargingEfficiencyKeys() default {"1.0"};
	
	@AttributeDefinition(name = "Discharging Inverter Efficiency Values", description = "Values for the efficiency lookup-table." +
	            " Has to have the same amount of entries as 'Efficiency Keys'.key[i] will map to value[i]")
	String[] dischargingEfficiencyValues() default {"1.0"};
	
	@AttributeDefinition(name="Charging Battery Efficiency", description = "Efficiency (Not Including Inverter) as percentage.")
	double batteryChargingEfficiency() default 1.0;
	
	@AttributeDefinition(name = "Discharging Battery Efficiency", description = "Efficiency (Not Including Inverter) as percentage.")
	double batteryDischargingEfficiency() default 1.0;
	
	@AttributeDefinition(name = "Upper SoC Borders", description ="Denotes SoC at which the battery will change to higher SoCState")
	String[] higherSocBorder() default {"20","50"};

	@AttributeDefinition(name = "Lower SoC Borders", description ="Denotes SoC at which the battery will change to lower SoCState")
	String[] lowerSocBorder()  default {"20","50"};
}

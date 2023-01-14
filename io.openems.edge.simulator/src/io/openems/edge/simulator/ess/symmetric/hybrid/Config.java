package io.openems.edge.simulator.ess.symmetric.hybrid;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.common.sum.GridMode;

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

	@AttributeDefinition(name = "Initial State of Charge [%]")
	int initialSoc() default 50;

	@AttributeDefinition(name = "Grid mode")
	GridMode gridMode() default GridMode.ON_GRID;

	String webconsole_configurationFactory_nameHint() default "Simulator EssSymmetric Reacting [{id}]";

	@AttributeDefinition(name ="Allowed Discharge Power", description="Maximum amount of power in [W] this ESS can discharge. Has to be >= 0", min = "0")
	int allowedDischargePower();

	@AttributeDefinition(name="Allowed Charge Power", description="Maximum amount of power in [W] this ESS can be charged with. Has to be <=0", max = "0")
	int allowedChargePower();
}

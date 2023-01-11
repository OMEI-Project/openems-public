package io.openems.edge.controller.ess.hybridess.controller;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller HybridController", //
		description = "Controller for Hybrid ESS consisting of one Redox-ESS and one LiOn-ESS")
public
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlHybridController0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";
	
	@AttributeDefinition(name = "Main-Ess", description = "ID of Main-Ess. Ess with high capacity, providing power for netload.")
	String mainId();
	
	@AttributeDefinition(name = "Support-Ess", description = "ID of Support-Ess. Ess with high power output.")
	String supportId();

	@AttributeDefinition(name = "Grid-Meter-ID", description = "ID of the Grid-Meter.")
	String meterId();

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;
	
	@AttributeDefinition(name="Energy Prediction", description = "File path to CSVFile containing the energy prediction. Fields 'Start', 'End', 'Energy'")
	String energyPrediction();
	
	@AttributeDefinition(name="Power Prediction", description = "File path to CSVFile containing the energy prediction. Fields 'Start', 'End', 'Power'")
	String powerPrediction();
	
	@AttributeDefinition(name = "Default Minimum Energy", 
			description = "Minimal total Energy in Wh that should be stored by ESSs to ensure EVs can be serviced.", min="0")
	int defaultMinimumEnergy() default 100_000;
	
	@AttributeDefinition(name ="Maximum Grid Power", description = "Maximum power that can be drawn from grid in W.")
	int maxGridPower() default 200_000;

	String webconsole_configurationFactory_nameHint() default "Controller HybridController [{id}]";

}
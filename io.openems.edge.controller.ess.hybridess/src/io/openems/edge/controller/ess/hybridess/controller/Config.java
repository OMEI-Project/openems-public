package io.openems.edge.controller.ess.hybridess.controller;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller HybridController", //
		description = "Controller for Hybrid ESS with single battery system")
public
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlHybridController0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";
	
	// Commented out for future reactivation if dual-battery mode is needed
	// @AttributeDefinition(name = "Main-Ess", description = "ID of Main-Ess. Ess with high capacity, providing power for netload.")
	// String mainId();
	
	@AttributeDefinition(name = "Support-Ess", description = "ID of Support-Ess (Lithium Battery)")
	String supportId();

	@AttributeDefinition(name = "Grid-Meter-ID", description = "ID of the Grid-Meter.")
	String meterId();

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;
	
	@AttributeDefinition(name = "Default Minimum Energy", 
			description = "Minimal total Energy in Wh that should be stored by ESS to ensure EVs can be serviced.", min="0")
	int defaultMinimumEnergy() default 100_000;
	
	@AttributeDefinition(name ="Maximum Grid Power", description = "Maximum power that can be drawn from grid in W.")
	int maxGridPower() default 200_000;

	@AttributeDefinition(name = "Data Acquisition URL", description = "Base URL for the external data acquisition service (e.g., http://127.0.0.1:5000/). Specific paths like '/logdata' or '/should_charge_now' will be appended.")
	String dataAcquisitionServiceBaseUrl() default "http://127.0.0.1:5000/";

	String webconsole_configurationFactory_nameHint() default "Controller HybridController [{id}]";

}
package io.openems.edge.adapter.edge2edge.hybrid;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
    name = "Edge2Edge ESS Hybrid Adapter",
    description = "Adapter that makes Edge2EdgeEss compatible with Hybrid ESS controllers like HybridController"
)
@interface Config {
    @AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
    String id() default "edge2edgeHybridAdapter0";

    @AttributeDefinition(name = "Alias", description = "Human-readable name of this Component")
    String alias() default "Edge2Edge Hybrid Adapter";

    @AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
    boolean enabled() default true;

    @AttributeDefinition(name = "Edge2Edge ESS ID", description = "Component ID of the Edge2EdgeEss to wrap")
    String wrappedEssId() default "edge2edgeEss0";

    @AttributeDefinition(name = "Lower SoC Bounds", description = "Lower SoC thresholds [RED->ORANGE, ORANGE->GREEN] in %")
    int[] lowerSocBounds() default {15, 50};

    @AttributeDefinition(name = "Upper SoC Bounds", description = "Upper SoC thresholds [RED->ORANGE, ORANGE->GREEN] in %") 
    int[] upperSocBounds() default {25, 55};

    @AttributeDefinition(name = "Max Power Change Per Cycle", description = "Maximum power change per cycle in W for ramping")
    int maxPowerChangePerCycle() default 1000;

    String webconsole_configurationFactory_nameHint() default "Edge2Edge ESS Hybrid Adapter [{id}]";
} 
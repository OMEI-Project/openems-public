package io.openems.edge.simulator.datasource.live.api;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
    name = "Simulator DataSource: Live API", 
    description = "This service fetches live data from an API endpoint."
)
public @interface Config {

    @AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
    String id() default "liveDatasource0";

    @AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
    String alias() default "";

    @AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
    boolean enabled() default true;

    @AttributeDefinition(
        name = "Time-Delta", 
        description = "Time delta (in seconds) between data updates. The output value won’t change until this delta has passed.",
        required = false
    )
    int timeDelta() default -1;

    @AttributeDefinition(name = "API URL", description = "The URL of the API endpoint that provides the live data.")
    String apiUrl();

    @AttributeDefinition(name = "Timeout", description = "Connection timeout in milliseconds", required = false)
    int timeout() default 5000;
}

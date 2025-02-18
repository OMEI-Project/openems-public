package io.openems.edge.simulator.datasource.live.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.simulator.DataContainer;
import io.openems.edge.simulator.datasource.api.AbstractCsvDatasource;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;

/**
 * The LiveApiDatasource component is responsible for fetching live simulation data
 * from an external API endpoint. It extends the abstract CSV datasource to leverage
 * existing functionality for time-based updates and channel management.
 * 
 * This component implements SimulatorDatasource and EventHandler interfaces, meaning
 * it can be used as a data source in the OpenEMS simulation environment and is triggered
 * by specific events.
 */
@Designate(ocd = Config.class, factory = true)
@Component(name = "Simulator.Datasource.Live.API",
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE)
@EventTopics({ EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE })
public class LiveApiDatasource extends AbstractCsvDatasource implements SimulatorDatasource, EventHandler {

    /**
     * A reference to the ComponentManager which provides access to system time and other
     * common services in the OpenEMS environment.
     */
    @Reference
    private ComponentManager componentManager;

    /**
     * The configuration object injected by OSGi. This holds values such as the API URL,
     * timeout settings, and other parameters necessary for this data source.
     */
    private Config config;

    /**
     * Default constructor.
     * 
     * In this constructor, we pass an array of ChannelId values to the superclass.
     * The ChannelId is used to uniquely identify channels used by this component.
     */
    public LiveApiDatasource() {
        // Use the ChannelId enum values defined in OpenemsComponent. This allows the datasource
        // to support all the channel IDs predefined by the OpenEMS framework.
        super(OpenemsComponent.ChannelId.values());
    }

    /**
     * The activation method is called by the OSGi framework when the component is first
     * started. It initializes the component using configuration properties provided by OSGi.
     * 
     * @param context The component context provided by the OSGi runtime.
     * @param config  The configuration object containing the settings for this component.
     * @throws NumberFormatException If a number cannot be parsed from the configuration.
     * @throws IOException           If an I/O error occurs during initialization.
     */
    @Activate
    void activate(ComponentContext context, Config config) throws NumberFormatException, IOException {
        // Store the configuration for later use in the component.
        this.config = config;
        // Call the superclass activation method. This initializes important parameters like:
        // - The unique component ID
        // - The alias (or human-readable name)
        // - Whether the component is enabled or not
        // - The time delta, which controls the frequency of data updates.
        super.activate(context, config.id(), config.alias(), config.enabled(), config.timeDelta());
        // At this point, the datasource is fully initialized and ready to fetch data.
    }

    /**
     * This method returns the ComponentManager instance for the component.
     * The ComponentManager provides access to shared system resources such as clocks,
     * event dispatchers, and configuration management.
     * 
     * @return The ComponentManager instance.
     */
    @Override
    protected ComponentManager getComponentManager() {
        return this.componentManager;
    }

    /**
     * This method is responsible for fetching live data from the configured API endpoint.
     * It opens an HTTP connection, retrieves the JSON response, parses it, and then
     * loads the value into a DataContainer. The DataContainer is used by the OpenEMS
     * simulation engine to provide data to other components.
     * 
     * @return A DataContainer containing the fetched record(s).
     * @throws NumberFormatException If a numeric value in the JSON cannot be parsed.
     * @throws IOException           If an I/O error occurs during data retrieval or parsing.
     */
    @Override
    protected DataContainer getData() throws NumberFormatException, IOException {
        // Create a new instance of DataContainer to store the live data record.
        DataContainer result = new DataContainer();
        
        // Create a URL object from the API URL defined in the configuration.
        URL url = new URL(config.apiUrl());
        
        // Open an HTTP connection to the API endpoint.
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // Set the request method to GET as we are retrieving data.
        conn.setRequestMethod("GET");
        // Set connection timeout based on the configuration.
        conn.setConnectTimeout(config.timeout());
        // Set read timeout based on the configuration.
        conn.setReadTimeout(config.timeout());
        
        // Check if the HTTP response code indicates success (HTTP 200 OK).
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            // Use try-with-resources to ensure that streams are closed after use.
            try (InputStream is = conn.getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                // Parse the JSON response from the API.               
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                
                // Extract the "value" field from the JSON object.                
                float value = json.get("value").getAsFloat();
                
                // Add the parsed value to the DataContainer.
                // The record is represented as an array of Float objects.
                result.addRecord(new Float[] { value });
            }
        } else {
            // If the response code is not HTTP_OK, throw an IOException with a detailed message.
            throw new IOException("Failed to fetch data. Response code: " + conn.getResponseCode());
        }
        
        // Disconnect the HTTP connection to free resources.
        conn.disconnect();
        
        // Return the DataContainer containing the new record.
        return result;
    }
}

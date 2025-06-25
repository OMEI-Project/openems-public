package io.openems.edge.adapter.edge2edge.hybrid;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.edge2edge.ess.Edge2EdgeEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid;
import io.openems.edge.ess.api.SoCStateMachine;
import io.openems.edge.ess.api.SocState;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Power;

/**
 * Edge2Edge ESS Hybrid Adapter
 * 
 * This adapter wraps an Edge2EdgeEss component and implements ManagedSymmetricEssHybrid
 * to make it compatible with controllers that require hybrid ESS functionality like HybridController.
 * 
 * The adapter adds missing hybrid-specific methods like getSocState() and filterPower()
 * while delegating most other functionality to the wrapped Edge2EdgeEss.
 */
@Designate(ocd = Config.class, factory = true)
@Component(
    name = "Edge2Edge.Ess.HybridAdapter", 
    immediate = true, 
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class Edge2EdgeHybridAdapter extends AbstractOpenemsComponent 
    implements ManagedSymmetricEssHybrid, ManagedSymmetricEss, SymmetricEss, OpenemsComponent, StartStoppable, ModbusSlave {

    private final Logger log = LoggerFactory.getLogger(Edge2EdgeHybridAdapter.class);

    /**
     * Additional ChannelIds for adapter-specific functionality
     */
    public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
        ADAPTER_DEBUG_MESSAGE(Doc.of(OpenemsType.STRING).text("Debug message from adapter")),
        WRAPPED_ESS_ID(Doc.of(OpenemsType.STRING).text("ID of wrapped Edge2EdgeEss component")),
        POWER_FILTER_STATUS(Doc.of(OpenemsType.STRING).text("Status of power filtering/ramping"));

        private final Doc doc;

        private ChannelId(Doc doc) {
            this.doc = doc;
        }

        @Override
        public Doc doc() {
            return this.doc;
        }
    }

    @Reference
    private ComponentManager componentManager;

    @Reference
    private ConfigurationAdmin cm;

    private Config config;
    private ManagedSymmetricEss wrappedEss;
    private SoCStateMachine socStateMachine;
    private int lastPower = 0;
    private int maxPowerChangePerCycle = 1000;

    public Edge2EdgeHybridAdapter() {
        super(
            OpenemsComponent.ChannelId.values(),
            SymmetricEss.ChannelId.values(),
            ManagedSymmetricEss.ChannelId.values(),
            ManagedSymmetricEssHybrid.ChannelId.values(),
            StartStoppable.ChannelId.values(),
            ChannelId.values()
        );
    }

    @Activate
    void activate(ComponentContext context, Config config) throws OpenemsException {
        this.config = config;
        super.activate(context, config.id(), config.alias(), config.enabled());
        
        // Get the wrapped Edge2EdgeEss component
        try {
            this.wrappedEss = this.componentManager.getComponent(config.wrappedEssId());
        } catch (OpenemsNamedException e) {
            throw new OpenemsException("Failed to get wrapped Edge2EdgeEss component: " + config.wrappedEssId(), e);
        }
        
        // Initialize SoC state machine with configurable thresholds
        this.socStateMachine = new SoCStateMachine(config.lowerSocBounds(), config.upperSocBounds());
        this.maxPowerChangePerCycle = config.maxPowerChangePerCycle();
        
        // Set adapter metadata
        this.channel(ChannelId.WRAPPED_ESS_ID).setNextValue(config.wrappedEssId());
        
        this.logInfo(this.log, "Edge2Edge Hybrid ESS Adapter activated, wrapping: " + config.wrappedEssId());
    }

    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    // ========== Delegate Most Methods to Wrapped ESS ==========
    
    @Override
    public Value<Integer> getSoc() {
        return wrappedEss.getSoc();
    }

    @Override
    public Value<Integer> getCapacity() {
        return wrappedEss.getCapacity();
    }

    @Override
    public Value<Integer> getActivePower() {
        return wrappedEss.getActivePower();
    }

    @Override
    public Value<Integer> getReactivePower() {
        return wrappedEss.getReactivePower();
    }

    @Override
    public Value<Integer> getAllowedChargePower() {
        return wrappedEss.getAllowedChargePower();
    }

    @Override
    public Value<Integer> getAllowedDischargePower() {
        return wrappedEss.getAllowedDischargePower();
    }

    @Override
    public Value<Integer> getMaxApparentPower() {
        return wrappedEss.getMaxApparentPower();
    }

    @Override
    public GridMode getGridMode() {
        return wrappedEss.getGridMode();
    }

    @Override
    public void setActivePowerEquals(Integer setActivePowerEquals) throws OpenemsNamedException {
        wrappedEss.setActivePowerEquals(setActivePowerEquals);
    }

    @Override
    public void setReactivePowerEquals(Integer setReactivePowerEquals) throws OpenemsNamedException {
        wrappedEss.setReactivePowerEquals(setReactivePowerEquals);
    }

    @Override
    public Power getPower() {
        return wrappedEss.getPower();
    }

    @Override
    public int getPowerPrecision() {
        return wrappedEss.getPowerPrecision();
    }

    @Override
    public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
        wrappedEss.applyPower(activePower, reactivePower);
    }

    // ========== Implement Missing Hybrid-Specific Methods ==========

    @Override
    public SocState getSocState() {
        Integer soc = this.getSoc().orElse(50); // Default to 50% if undefined
        socStateMachine.calculateSoCState(soc);
        SocState state = socStateMachine.getSoCState();
        this.channel(ManagedSymmetricEssHybrid.ChannelId.SOC_STATE).setNextValue(state);
        return state;
    }

    @Override
    public int filterPower(int targetPower) {
        // Simple power ramping implementation to prevent sudden power changes
        int powerDifference = targetPower - lastPower;
        
        String debugMessage = String.format("FilterPower: target=%dW, last=%dW, diff=%dW, maxChange=%dW", 
                                           targetPower, lastPower, powerDifference, maxPowerChangePerCycle);
        
        if (Math.abs(powerDifference) <= maxPowerChangePerCycle) {
            // Within ramp rate limits - use target power
            lastPower = targetPower;
            debugMessage += " -> Using target: " + targetPower + "W";
            this.channel(ChannelId.POWER_FILTER_STATUS).setNextValue("TARGET_USED");
        } else {
            // Apply ramp rate limiting
            if (powerDifference > 0) {
                // Ramping up (more positive/less negative)
                lastPower += maxPowerChangePerCycle;
            } else {
                // Ramping down (more negative/less positive)
                lastPower -= maxPowerChangePerCycle;
            }
            debugMessage += " -> Ramped to: " + lastPower + "W";
            this.channel(ChannelId.POWER_FILTER_STATUS).setNextValue("RAMPED");
        }
        
        this.channel(ChannelId.ADAPTER_DEBUG_MESSAGE).setNextValue(debugMessage);
        return lastPower;
    }

    // ========== Implement Hybrid-Specific Channels ==========
    // Note: Most methods are provided by default implementations in ManagedSymmetricEssHybrid interface

    // Internal channel setters - use interface default methods
    private void _setUpperPossibleChargePowerLimit(Integer value) {
        this.channel(ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT).setNextValue(value);
    }

    private void _setLowerPossibleChargePowerLimit(Integer value) {
        this.channel(ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT).setNextValue(value);
    }

    private void _setUpperPossibleDischargePowerLimit(Integer value) {
        this.channel(ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT).setNextValue(value);
    }

    private void _setLowerPossibleDischargePowerLimit(Integer value) {
        this.channel(ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT).setNextValue(value);
    }

    private void _setEfficiency(Double value) {
        this.channel(ManagedSymmetricEssHybrid.ChannelId.EFFICIENCY).setNextValue(value);
    }

    private void _setInefficiencyPowerLoss(Integer value) {
        this.channel(ManagedSymmetricEssHybrid.ChannelId.INEFFICIENCY_POWER_LOSS).setNextValue(value);
    }

    // ========== StartStoppable Implementation ==========

    @Override
    public void setStartStop(io.openems.edge.common.startstop.StartStop value) throws OpenemsNamedException {
        if (wrappedEss instanceof StartStoppable) {
            ((StartStoppable) wrappedEss).setStartStop(value);
        }
    }

    // ========== ModbusSlave Implementation ==========

    @Override
    public ModbusSlaveTable getModbusSlaveTable(io.openems.common.channel.AccessMode accessMode) {
        return new ModbusSlaveTable(
            OpenemsComponent.getModbusSlaveNatureTable(accessMode),
            SymmetricEss.getModbusSlaveNatureTable(accessMode),
            ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode),
            StartStoppable.getModbusSlaveNatureTable(accessMode)
        );
    }

    @Override
    public String debugLog() {
        return "HybridAdapter[" + config.wrappedEssId() + "] " +
               "SoC:" + this.getSoc().asString() +
               "|SoCState:" + this.getSocState() +
               "|L:" + this.getActivePower().asString() +
               "|Allowed:" + this.getAllowedChargePower().asStringWithoutUnit() + ";" +
               this.getAllowedDischargePower().asString();
    }

    // Called by the framework each cycle
    public void _cycle() {
        // Update hybrid-specific channels each cycle
        this.updateHybridChannels();
        this.getSocState(); // Update SoC state
    }

    // Update hybrid-specific channels based on wrapped ESS state
    private void updateHybridChannels() {
        Integer allowedCharge = wrappedEss.getAllowedChargePower().orElse(0);
        Integer allowedDischarge = wrappedEss.getAllowedDischargePower().orElse(0);
        
        // Set power limits based on wrapped ESS capabilities
        // For charging: upper limit is 0 (less charging), lower limit is more negative (more charging)
        this._setUpperPossibleChargePowerLimit(0);
        this._setLowerPossibleChargePowerLimit(allowedCharge);
        
        // For discharging: upper limit is max discharge, lower limit is 0
        this._setUpperPossibleDischargePowerLimit(allowedDischarge);
        this._setLowerPossibleDischargePowerLimit(0);
        
        // Update efficiency and loss placeholders
        this._setEfficiency(1.0); // Assume 100% efficiency for Edge2Edge
        this._setInefficiencyPowerLoss(0); // No losses for Edge2Edge
    }
} 
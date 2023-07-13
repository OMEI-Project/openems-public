package io.openems.edge.simulator.pythonBridge;

import io.openems.common.exceptions.OpenemsError;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.power.api.*;
import io.openems.edge.ess.test.DummyPower;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import java.util.ArrayList;
import java.util.List;

public class SimulatedPower extends DummyPower implements Power, EventHandler {

    private final List<ManagedSymmetricEss> esss = new ArrayList<>();

    @Override
    public void handleEvent(Event event) {
        if (event.getTopic().equals(EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE)) {
            for (ManagedSymmetricEss ess : this.esss) {
                int activePower = ess.getSetActivePowerEqualsChannel().getNextWriteValue().orElse(0);
                int reactivePower = ess.getSetReactivePowerEqualsChannel().getNextWriteValue().orElse(0);
                try {
                    ess.applyPower(activePower, reactivePower);
                } catch (OpenemsError.OpenemsNamedException e) {
                    this.log.warn("Error in Ess [" + ess.id() + "] apply power: " + e.getMessage());

                    // announce running failed
                    ess._setApplyPowerFailed(true);
                }
            }
        }
    }

    @Override
    public void addEss(ManagedSymmetricEss ess) {
        esss.add(ess);
    }
}

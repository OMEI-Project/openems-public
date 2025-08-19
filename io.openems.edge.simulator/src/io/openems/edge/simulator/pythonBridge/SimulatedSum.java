package io.openems.edge.simulator.pythonBridge;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.Sum;
public class SimulatedSum extends AbstractOpenemsComponent implements Sum {

    public SimulatedSum() {
        super(
                OpenemsComponent.ChannelId.values(), //
                Sum.ChannelId.values() //
        );
    }

    @Override
    public void updateChannelsBeforeProcessImage() {
        // Do nothing
    }

    public void setProduction(int production){
        this._setProductionActivePower(production);
    }

    public void setConsumption(int consumption){
        this._setConsumptionActivePower(consumption);
    }
}

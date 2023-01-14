package io.openems.edge.simulator.ess.symmetric.reacting.hybrid;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.test.AbstractComponentTest;
import io.openems.edge.simulator.ess.symmetric.hybrid.EssSymmetricHybrid;

public class ManagedSymmetricEssHybridTest extends AbstractComponentTest<ManagedSymmetricEssHybridTest, EssSymmetricHybrid> {

	public ManagedSymmetricEssHybridTest(EssSymmetricHybrid sut) throws OpenemsException {
		super(sut);
	}

	@Override
	protected void onBeforeWrite() throws OpenemsNamedException {
		var ess = this.getSut();
		int activePower = ess.getSetActivePowerEqualsChannel().getNextWriteValueAndReset().orElse(0);
		int reactivePower = ess.getSetReactivePowerEqualsChannel().getNextWriteValueAndReset().orElse(0);

		int allowedChargePower = ess.getAllowedChargePower().orElse(0);
		if (activePower < allowedChargePower) {
			activePower = allowedChargePower;
		}

		int allowedDischargePower = ess.getAllowedDischargePower().orElse(0);
		if (activePower > allowedDischargePower) {
			activePower = allowedDischargePower;
		}

		this.getSut().applyPower(activePower, reactivePower);
	}

	@Override
	protected ManagedSymmetricEssHybridTest self() {
		return this;
	}

}
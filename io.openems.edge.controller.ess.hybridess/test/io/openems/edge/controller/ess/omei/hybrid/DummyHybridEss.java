package io.openems.edge.controller.ess.omei.hybrid;

import java.util.function.Consumer;

import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.ess.test.DummyPower;

public class DummyHybridEss extends AbstractOpenemsComponent
implements ManagedSymmetricEssHybrid, ManagedSymmetricEss, SymmetricEss, OpenemsComponent {

	private final Power power;
	private String id;
	private final int powerPrecision = 1;

	// always true -> no response time testable in controller test. For this ESS test.
	private boolean ready = true;

	protected DummyHybridEss(String id, Power power,
			io.openems.edge.common.channel.ChannelId[] firstInitialChannelIds,
			io.openems.edge.common.channel.ChannelId[]... furtherInitialChannelIds) {
		super(firstInitialChannelIds, furtherInitialChannelIds);
		this.power = power;
		if (power instanceof DummyPower) {
			((DummyPower) power).addEss(this);
		}
		for (Channel<?> channel : this.channels()) {
			channel.nextProcessImage();
		}
		super.activate(null, id, "", true);
	}
	
	public DummyHybridEss(String id, Power power) {
		this(id, power, //
				OpenemsComponent.ChannelId.values(), //
				ManagedSymmetricEssHybrid.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(), //
				SymmetricEss.ChannelId.values() //
		);
	}


	@Override
	public Power getPower() {
		return this.power;
	}

	@Override
	public int getPowerPrecision() {
		return this.powerPrecision;
	}

	// FROM HERE copied and adapted from DummyManagedSymmetricEss
	
	private Consumer<SymmetricApplyPowerRecord> symmetricApplyPowerCallback = null;
	
	/**
	 * Set callback for applyPower() of this {@link DummyManagedSymmetricEss}.
	 *
	 * @param callback the callback
	 * @return myself
	 */
	public DummyHybridEss withSymmetricApplyPowerCallback(Consumer<SymmetricApplyPowerRecord> callback) {
		this.symmetricApplyPowerCallback = callback;
		return this;
	}

	@Override
	public void applyPower(int activePower, int reactivePower) {
		if (this.symmetricApplyPowerCallback != null) {
			this.symmetricApplyPowerCallback.accept(new SymmetricApplyPowerRecord(activePower, reactivePower));
		}
	}

	@Override
	public int filterPower(int targetPower) {

		/*
		 * Copy and paste from EssSymmetric. Bad Practice - Don't know how to do it otherwise
		 * alternative: Implement filteredPower as channel, make filterPower void, set and read value
		 * in Test via channel.
		 */
		int filteredPower = targetPower;
		int upperLimit = 0;
		int lowerLimit = 0;

		if(!ready && targetPower != 0) {
			beginStartTimer();
			filteredPower = 0;
		} else if (targetPower >= 0){
			// Discharging

			upperLimit = this.getUpperPossibleDischargePower().orElse(0);
			lowerLimit = this.getLowerPossibleDischargePower().orElse(0);
		} else {
			// Charging

			upperLimit = this.getUpperPossibleChargePower().orElse(0);
			lowerLimit = this.getLowerPossibleChargePower().orElse(0);
		}

		if(targetPower > upperLimit) {
			filteredPower = upperLimit;
		} else if(targetPower < lowerLimit) {
			filteredPower = lowerLimit;
		}

		return filteredPower;
	}

	private void beginStartTimer() {
	}

	public static class SymmetricApplyPowerRecord {
		public final int activePower;
		public final int reactivePower;

		public SymmetricApplyPowerRecord(int activePower, int reactivePower) {
			this.activePower = activePower;
			this.reactivePower = reactivePower;
		}
	}
}

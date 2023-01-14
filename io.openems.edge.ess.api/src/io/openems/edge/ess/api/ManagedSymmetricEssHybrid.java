package io.openems.edge.ess.api;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;

public interface ManagedSymmetricEssHybrid extends ManagedSymmetricEss {
	
	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		
		// TODO Naming scheme might be misleading. Charging the upperlimit actually results in less power charged.
		
		/**
		 * Holds the upper limit of power which can be charged this cycle.
		 * Range in [{@link ChannelId#ALLOWED_CHARGE_POWER},0]
		 */
		UPPER_POSSIBLE_CHARGE_POWER_LIMIT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)),
		
		/**
		 * Holds the lower limit of power which can be charged this cycle.
		 * Range in [{@link ChannelId#ALLOWED_CHARGE_POWER},0]
		 */
		LOWER_POSSIBLE_CHARGE_POWER_LIMIT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)),
		
		/**
		 * Holds the upper limit of power which can be discharged this cycle.
		 * Range in [0, {@link ChannelId#ALLOWED_CHARGE_POWER}]
		 */
		UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)),
		
		/**
		 * Holds the lower limit of power which can be discharged this cycle.
		 * Range in [0, {@link ChannelId#ALLOWED_CHARGE_POWER}]
		 */
		LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT))
				
		;
		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	public int filterPower(int targetPower);
	
	/**
	 * Gets upper boundary of possible charge power for this cycle.
	 * 
	 * Range in [{@link ChannelId#ALLOWED_CHARGE_POWER},0]
	 * 
	 * @return Returns upper boundary of possible charge power in [W].
	 */
	public default Value<Integer> getUpperPossibleChargePower() {
		return this.getUpperPossibleChargePowerChannel().value();
	}
	
	/**
	 * Gets lower boundary of possible charge power for this cycle.
	 * 
	 * Range in [{@link ChannelId#ALLOWED_CHARGE_POWER},0]
	 * 
	 * @return Returns lower boundary of possible charge power in [W].
	 */
	public default Value<Integer> getLowerPossibleChargePower() {
		return this.getLowerPossibleChargePowerChannel().value();
	}
	
	/**
	 * Returns upper boundary of possible discharge power for this cycle.
	 * Range in [0, {@link ChannelId#ALLOWED_CHARGE_POWER}]
	 * 
	 * @return Returns upper boundary of possible discharge power in [W].
	 */
	public default Value<Integer> getUpperPossibleDischargePower() {
		return this.getUpperPossibleDischargePowerChannel().value();
	}
	
	/**
	 * Returns upper boundary of possible discharge power for this cycle.
	 * Range in [0, {@link ChannelId#ALLOWED_CHARGE_POWER}]
	 * 
	 * @return Returns lower boundary of possible discharge power in [W].
	 */
	public default Value<Integer> getLowerPossibleDischargePower() {
		return this.getLowerPossibleDischargePowerChannel().value();
	}
	
	public default void _setUpperPossibleChargePower(Integer value) {
		this.channel(ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT).setNextValue(value);
	}
	public default void _setLowerPossibleChargePower(Integer value) {
		this.channel(ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT).setNextValue(value);
	}
	public default void _setUpperPossibleDischargePower(Integer value) {
		this.channel(ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT).setNextValue(value);
	}
	public default void _setLowerPossibleDischargePower(Integer value) {
		this.channel(ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT).setNextValue(value);
	}
	
	public default IntegerReadChannel getUpperPossibleChargePowerChannel() {
		return channel(ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT);
	}
	
	public default IntegerReadChannel getLowerPossibleChargePowerChannel() {
		return channel(ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT);
	}
	
	public default IntegerReadChannel getUpperPossibleDischargePowerChannel() {
		return channel(ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT);
	}
	
	public default IntegerReadChannel getLowerPossibleDischargePowerChannel() {
		return channel(ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT);
	}

}

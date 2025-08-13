package io.openems.edge.evcs.technagon;

import static io.openems.common.channel.AccessMode.WRITE_ONLY;
import static io.openems.common.channel.Unit.MILLIAMPERE;
import static io.openems.common.types.OpenemsType.INTEGER;

import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.evcs.technagon.enums.TechnagonConnectorType;
import io.openems.edge.evcs.technagon.enums.TechnagonState;

public interface EvcsTechnagon extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		VENDOR(Doc.of(OpenemsType.STRING)), //
		DEVICE(Doc.of(OpenemsType.STRING)), //
		MODBUS_REGISTER_LAYOUT_MAJOR_VERSION(Doc.of(OpenemsType.INTEGER)), //
		MODBUS_REGISTER_LAYOUT_MINOR_VERSION(Doc.of(OpenemsType.INTEGER)), //
		STATION_SERIAL_NUMBER(Doc.of(OpenemsType.INTEGER)), //
		MODBUS_REGISTER_LAYOUT_VERSION(Doc.of(OpenemsType.INTEGER)), //
		STATION_MAX_CURRENT(Doc.of(OpenemsType.DOUBLE).unit(Unit.AMPERE)), //
		NUM_EVSES(Doc.of(OpenemsType.INTEGER)), //
		EVSE_SERIAL_NUMBER(Doc.of(OpenemsType.INTEGER)), //
		ACTIVE_CONNECTOR(Doc.of(TechnagonConnectorType.values())), //
		RAW_STATUS(Doc.of(TechnagonState.values())), //
		EVSE_STATUS_LAST_UPDATED(Doc.of(OpenemsType.LONG).unit(Unit.SECONDS)), //
		MIN_CHARGING_CURRENT(Doc.of(OpenemsType.INTEGER).unit(Unit.MILLIAMPERE)),
		MAX_CHARGING_CURRENT(Doc.of(OpenemsType.INTEGER).unit(Unit.MILLIAMPERE)),
		CURRENT_OFFERED(Doc.of(OpenemsType.DOUBLE).unit(Unit.MILLIAMPERE)), //
		POWER_FACTOR_L1(Doc.of(OpenemsType.DOUBLE).unit(Unit.PERCENT)), //
		POWER_FACTOR_L2(Doc.of(OpenemsType.DOUBLE).unit(Unit.PERCENT)), //
		POWER_FACTOR_L3(Doc.of(OpenemsType.DOUBLE).unit(Unit.PERCENT)), //
		FALL_BACK_CURRENT(Doc.of(OpenemsType.DOUBLE).unit(Unit.AMPERE)), //
		FALL_BACK_TIMEOUT(Doc.of(OpenemsType.INTEGER).unit(Unit.SECONDS)), //
		DEBUG_SET_CHARGING_CURRENT(Doc.of(INTEGER).unit(MILLIAMPERE)), //
		SET_CHARGING_CURRENT(Doc.of(INTEGER).unit(MILLIAMPERE).accessMode(WRITE_ONLY)
				.onChannelSetNextWriteMirrorToDebugChannel(DEBUG_SET_CHARGING_CURRENT));

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}

	}

	/**
	 * Gets the Channel for {@link ChannelId#SET_CHARGING_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerWriteChannel getSetChargingCurrentChannel() {
		return this.channel(ChannelId.SET_CHARGING_CURRENT);
	}

	/**
	 * Sets the next Write Value for {@link ChannelId#SET_CHARGING_CURRENT}.
	 * 
	 * @param current current to be set in mA
	 * @throws OpenemsNamedException on error
	 */
	public default void setSetChargingCurrent(int current) throws OpenemsNamedException {
		this.getSetChargingCurrentChannel().setNextWriteValue(current);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_CHARGING_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinChargingCurrentChannel() {
		return this.channel(ChannelId.MIN_CHARGING_CURRENT);
	}

	/**
	 * Gets the minimum current allowed by the hardware in mA. See
	 * {@link ChannelId#MIN_CHARGING_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinChargingCurrent() {
		return this.getMaxChargingCurrentChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_CHARGING_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxChargingCurrentChannel() {
		return this.channel(ChannelId.MAX_CHARGING_CURRENT);
	}

	/**
	 * Gets the maximum current allowed by the hardware in mA. See
	 * {@link ChannelId#MAX_CHARGING_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxChargingCurrent() {
		return this.getMaxChargingCurrentChannel().value();
	}
}

package io.openems.edge.evcs.technagon;


import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

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
		MIN_CURRENT(Doc.of(OpenemsType.DOUBLE).unit(Unit.AMPERE)),
		MAX_CURRENT(Doc.of(OpenemsType.DOUBLE).unit(Unit.AMPERE)),
		CURRENT_OFFERED(Doc.of(OpenemsType.DOUBLE).unit(Unit.MILLIAMPERE)), //
		POWER_FACTOR_L1(Doc.of(OpenemsType.DOUBLE).unit(Unit.PERCENT)), //
		POWER_FACTOR_L2(Doc.of(OpenemsType.DOUBLE).unit(Unit.PERCENT)), //
		POWER_FACTOR_L3(Doc.of(OpenemsType.DOUBLE).unit(Unit.PERCENT)), //
		FALL_BACK_CURRENT(Doc.of(OpenemsType.DOUBLE).unit(Unit.AMPERE)), //
		FALL_BACK_TIMEOUT(Doc.of(OpenemsType.INTEGER).unit(Unit.SECONDS)); //

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

}

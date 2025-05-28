package io.openems.edge.evcs.technagon;

public enum TechnagonChargingPoint {
	
	CHARGING_POINT_1(4096), //
	CHARGING_POINT_2(8192);
	
	private final int modbusAddressOffset;
	
	private TechnagonChargingPoint(int modbusAddressOffset) {
		 this.modbusAddressOffset = modbusAddressOffset;
	}
	
	/**
	 * Applies the configured connector offset to a Modbus register’s relative address.
	 *
	 * @param relativeModbusAddress the register address relative to the connector’s base address
	 * @return the absolute Modbus register address (relative + offset)
	 */
	public int applyModbusAddressOffset(int relativeModbusAddress) {
		return relativeModbusAddress + this.modbusAddressOffset;
	}
}

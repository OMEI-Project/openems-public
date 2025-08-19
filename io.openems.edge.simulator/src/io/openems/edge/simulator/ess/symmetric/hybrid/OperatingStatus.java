package io.openems.edge.simulator.ess.symmetric.hybrid;

import io.openems.common.types.OptionsEnum;

public enum OperatingStatus implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	SHUTDOWN(0, "Shutdown"),
	STANDBY(1, "Standby"),
	CHARGING(2, "Charging"),
	DISCHARGING(3, "Discharging"),
	ALARM(4, "Alarm"),;
	
	private int value;
	private String name;
	
	private OperatingStatus(int value,String name) {
		this.value = value;
		this.name = name;
	}

	@Override
	public int getValue() {
		return value;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return UNDEFINED;
	}
}

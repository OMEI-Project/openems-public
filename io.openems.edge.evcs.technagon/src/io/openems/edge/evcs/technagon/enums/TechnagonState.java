package io.openems.edge.evcs.technagon.enums;

import io.openems.common.types.OptionsEnum;

public enum TechnagonState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"), //
	UNAVAILABLE(0, "Unavailable"), //
	AVAILABLE(1, "Available"), //
	PREPARING(2, "Preparing"), //
	CHARGING(3, "Charging"), //
	EVSE_SUSPENDED(4, "EVSE Suspended"), //
	EV_SUSPENDED(5, "EV Suspended"), //
	FINISHING(6, "Finishing"), //
	RESERVED(10, "Reserved"), //
	FAULTED(99, "Faulted");

	private final int value;
	private final String name;

	private TechnagonState(int value, String name) {
		this.value = value;
		this.name = name;
	}
	
	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return UNDEFINED;
	}

}
package io.openems.edge.evcs.technagon.enums;

import io.openems.common.types.OptionsEnum;

public enum TechnagonConnectorType implements OptionsEnum {
	UNDEFINED(-1, "Undefined"), //
	TYPE_2(0, "Type 2"), //
	TYPE_F(1, "Type F"), //
	CCS(2, "CCS");

	private final int value;
	private final String name;

	private TechnagonConnectorType(int value, String name) {
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

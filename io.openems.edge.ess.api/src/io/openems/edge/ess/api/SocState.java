package io.openems.edge.ess.api;

import io.openems.common.types.OptionsEnum;

public enum SocState implements OptionsEnum {
    UNDEFINDED(-1, "UNDEFINED"),
    RED(0, "RED"),
    ORANGE(1, "ORANGE"),
    GREEN(2, "GREEN");

    private final int value;
    private final String name;

    private SocState(int value, String name) {
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
        return UNDEFINDED;
    }
}

package io.openems.edge.simulator.pythonBridge;

import io.openems.edge.common.sum.GridMode;
import io.openems.edge.simulator.ess.symmetric.hybrid.Config;

import java.lang.annotation.Annotation;

public class EssConfig implements Config {

    private final String id;
    private String alias;
    private final boolean enabled;
    private final int capacity;
    private final int rampRate;
    private final long responseTime;
    private final long inactivityTime;
    private final int minimumSoC;
    private final int maximumSoC;
    private final int initialSoC;
    private final int allowedDischargePower;
    private final int allowedChargePower;

    private final int[] lowerSocBorder;
    private final int[] higherSocBorder;

    public EssConfig(String id, String alias, boolean enabled, int capacity, int rampRate,
                     long responseTime, long inactivityTime,
                     int minimumSoC, int maximumSoC, int initialSoC,
                     int allowedDischargePower, int allowedChargePower,
                     int[] lowerSocBorder, int[] higherSocBorder) {
        this.id = id;
        this.alias = alias;
        this.enabled = enabled;
        this.capacity = capacity;
        this.rampRate = rampRate;
        this.responseTime = responseTime;
        this.inactivityTime = inactivityTime;
        this.minimumSoC = minimumSoC;
        this.maximumSoC = maximumSoC;
        this.initialSoC = initialSoC;
        this.allowedDischargePower = allowedDischargePower;
        this.allowedChargePower = allowedChargePower;
        this.lowerSocBorder = lowerSocBorder;
        this.higherSocBorder = higherSocBorder;
    }
    @Override
    public String id() {
        return this.id;
    }

    @Override
    public String alias() {
        return this.alias;
    }

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public int capacity() {
        return this.capacity;
    }

    @Override
    public int rampRate() {
        return this.rampRate;
    }

    @Override
    public long responseTime() {
        return this.responseTime;
    }

    @Override
    public long inactivityTime() {
        return this.inactivityTime;
    }

    @Override
    public int minimumSoc() {
        return this.minimumSoC;
    }

    @Override
    public int maximumSoc() {
        return this.maximumSoC;
    }

    @Override
    public int initialSoc() {
        return this.initialSoC;
    }

    @Override
    public GridMode gridMode() {
        return GridMode.ON_GRID;
    }

    @Override
    public String webconsole_configurationFactory_nameHint() {
        return "Config used for python bridge";
    }

    @Override
    public int allowedDischargePower() {
        return this.allowedDischargePower;
    }

    @Override
    public int allowedChargePower() {
        return this.allowedChargePower;
    }

    @Override
    public int[] higherSocBorder() {
        return this.higherSocBorder;
    }

    @Override
    public int[] lowerSocBorder() {
        return this.lowerSocBorder;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return null;
    }
}

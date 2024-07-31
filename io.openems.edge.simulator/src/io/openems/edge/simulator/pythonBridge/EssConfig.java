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
    private final double[] chargingEfficiencyKeys;
    private final double[] chargingEfficiencyValues;        
    private final double[] dischargingEfficiencyKeys;
    private final double[] dischargingEfficiencyValues;
    private final double batteryChargingEfficiency;
    private final double batteryDischargingEfficiency;
    private final int[] lowerSocBorder;
    private final int[] higherSocBorder;

    public EssConfig(String id, String alias, boolean enabled, int capacity, int rampRate,
                     long responseTime, long inactivityTime,
                     int minimumSoC, int maximumSoC, int initialSoC,
                     int allowedDischargePower, int allowedChargePower,
                     double[] chargingEfficiencyKeys, double[] chargingEfficiencyValues,
                     double[] dischargingEfficiencyKeys, double[] dischargingEfficiencyValues,
                     double batteryChargingEfficiency, double batteryDischargingEfficiency,
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
        this.chargingEfficiencyKeys = chargingEfficiencyKeys;
        this.chargingEfficiencyValues = chargingEfficiencyValues;
        this.dischargingEfficiencyKeys = dischargingEfficiencyKeys;
        this.dischargingEfficiencyValues = dischargingEfficiencyValues;
        this.batteryChargingEfficiency = batteryChargingEfficiency;
        this.batteryDischargingEfficiency = batteryDischargingEfficiency;
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
    
    /*@Override
    public double[] chargingEfficiencyKeys() {
            return this.chargingEfficiencyKeys ;
    }
    
    @Override
    public double[] chargingEfficiencyValues() {
            return this.chargingEfficiencyValues;
    }
    
    @Override
    public double[] dischargingEfficiencyKeys() {
            return this.dischargingEfficiencyKeys ;
    }
    
    @Override
    public double[] dischargingEfficiencyValues() {
            return this.dischargingEfficiencyValues;
    }*/
    
    @Override
	public double batteryChargingEfficiency() {
		return this.batteryChargingEfficiency;
	}

	@Override
	public double batteryDischargingEfficiency() {
		return this.batteryDischargingEfficiency;
	}

    @Override
    public String[] higherSocBorder() {
        return toStringArray(higherSocBorder);
    }

    @Override
    public String[] lowerSocBorder() {
    	return toStringArray(lowerSocBorder);
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return null;
    }
    
    public static String[] toStringArray(double[] doubleArray) {
        String[] stringArray = new String[doubleArray.length];
        for (int i = 0; i < doubleArray.length; i++) {
            stringArray[i] = Double.toString(doubleArray[i]);
        }
        return stringArray;
    }

    public static String[] toStringArray(int[] intArray) {
        String[] stringArray = new String[intArray.length];
        for (int i = 0; i < intArray.length; i++) {
            stringArray[i] = Integer.toString(intArray[i]);
        }
        return stringArray;
    }
}

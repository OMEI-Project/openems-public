package io.openems.edge.simulator.ess.symmetric.hybrid;

import java.util.Map;
import java.util.TreeMap;

public class EfficiencyTable {

    private final TreeMap<Double, Double> efficiencies;

    public EfficiencyTable(Map<Double, Double> efficiencies) {
        this.efficiencies = new TreeMap<Double, Double>(efficiencies);
    }

    public EfficiencyTable(double[] keys, double[] values){
        if (keys.length != values.length) {
            throw new IllegalArgumentException("Amount of keys and values for EfficiencyTable are not equal");
        }
        if (keys.length == 0) {
            keys = new double[]{1.0};
            values = new double[]{1.0};
        }

        efficiencies = new TreeMap<>();
        for(int i = 0; i < keys.length; i++) {
            if (values[i] < 0 || values[i] > 1) {
                throw new IllegalArgumentException(String.format("Efficiency value has to be within [0,1] but was %s", values[i]));
            }
            if (keys[i] < 0 || keys[i] > 1) {
                throw new IllegalArgumentException(String.format("Efficiency key has to be within [0,1] but was %s", keys[i]));
            }
            efficiencies.put(keys[i], values[i]);
        }
    }

    public double getEfficiency(double givenKey) {
        if (givenKey < 0 || givenKey > 1) {
            throw new IllegalArgumentException(String.format("Given key has to be within [0,1] but was %s", givenKey));
        }

        Map.Entry<Double, Double> floorEntry = efficiencies.floorEntry(givenKey);
        Map.Entry<Double, Double> ceilingEntry = efficiencies.ceilingEntry(givenKey);

        if (floorEntry == null) {
            return ceilingEntry.getValue();
        }
        if (ceilingEntry == null) {
            return floorEntry.getValue();
        }

        double floorKey = floorEntry.getKey();
        double ceilingKey = ceilingEntry.getKey();

        // Find the closest key
        if (Math.abs(givenKey - floorKey) < Math.abs(givenKey - ceilingKey)) {
            return floorEntry.getValue();
        } else {
            return ceilingEntry.getValue();
        }
    }

}
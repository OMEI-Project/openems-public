package io.openems.edge.controller.ess.omei.hybrid;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.controller.ess.hybridess.controller.Config;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String supportId;
		private String energyPrediction;
		private String powerPrediction;
		private int defaultMinimumEnergy;
		private int maxGridPower;
		private String dataAcquisitionServiceBaseUrl;
		private int dataServiceInterval;
		private int[] lowerSocBounds;
		private int[] upperSocBounds;
		private int maxPowerChangePerCycle;
		private int maxChargePower;
		private int maxDischargePower;
		private double[] chargingEfficiencyKeys;
		private double[] chargingEfficiencyValues;
		private double[] dischargingEfficiencyKeys;
		private double[] dischargingEfficiencyValues;
		private double batteryChargingEfficiency;
		private double batteryDischargingEfficiency;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}
		
		public Builder setSupportId(String supportId) {
			this.supportId = supportId;
			return this;
		}
		
		public Builder setEnergyPrediction(String energyPrediction) {
			this.energyPrediction = energyPrediction;
			return this;
		}
		
		public Builder setPowerPrediction(String powerPrediction) {
			this.powerPrediction = powerPrediction;
			return this;
		}
		
		public Builder setDefaultMinimumEnergy(int defaultMinimumEnergy) {
			this.defaultMinimumEnergy = defaultMinimumEnergy;
			return this;
		}
		
		public Builder setMaxGridPower(int maxGridPower) {
			this.maxGridPower = maxGridPower;
			return this;
		}

		public Builder setDataAcquisitionServiceBaseUrl(String dataAcquisitionServiceBaseUrl) {
			this.dataAcquisitionServiceBaseUrl = dataAcquisitionServiceBaseUrl;
			return this;
		}

		public Builder setDataServiceInterval(int dataServiceInterval) {
			this.dataServiceInterval = dataServiceInterval;
			return this;
		}

		public Builder setLowerSocBounds(int[] lowerSocBounds) {
			this.lowerSocBounds = lowerSocBounds;
			return this;
		}

		public Builder setUpperSocBounds(int[] upperSocBounds) {
			this.upperSocBounds = upperSocBounds;
			return this;
		}

		public Builder setMaxPowerChangePerCycle(int maxPowerChangePerCycle) {
			this.maxPowerChangePerCycle = maxPowerChangePerCycle;
			return this;
		}

		public Builder setMaxChargePower(int maxChargePower) {
			this.maxChargePower = maxChargePower;
			return this;
		}

		public Builder setMaxDischargePower(int maxDischargePower) {
			this.maxDischargePower = maxDischargePower;
			return this;
		}

		public Builder setChargingEfficiencyKeys(double[] chargingEfficiencyKeys) {
			this.chargingEfficiencyKeys = chargingEfficiencyKeys;
			return this;
		}

		public Builder setChargingEfficiencyValues(double[] chargingEfficiencyValues) {
			this.chargingEfficiencyValues = chargingEfficiencyValues;
			return this;
		}

		public Builder setDischargingEfficiencyKeys(double[] dischargingEfficiencyKeys) {
			this.dischargingEfficiencyKeys = dischargingEfficiencyKeys;
			return this;
		}

		public Builder setDischargingEfficiencyValues(double[] dischargingEfficiencyValues) {
			this.dischargingEfficiencyValues = dischargingEfficiencyValues;
			return this;
		}

		public Builder setBatteryChargingEfficiency(double batteryChargingEfficiency) {
			this.batteryChargingEfficiency = batteryChargingEfficiency;
			return this;
		}

		public Builder setBatteryDischargingEfficiency(double batteryDischargingEfficiency) {
			this.batteryDischargingEfficiency = batteryDischargingEfficiency;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 * 
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String id() {
		return this.builder.id;
	}
	
	@Override
	public String supportId() {
		return this.builder.supportId;
	}

	@Override
	public int defaultMinimumEnergy() {
		return this.builder.defaultMinimumEnergy;
	}

	@Override
	public int maxGridPower() {
		return this.builder.maxGridPower;
	}

	@Override
	public String dataAcquisitionServiceBaseUrl() {
		return this.builder.dataAcquisitionServiceBaseUrl;
	}

	@Override
	public int dataServiceInterval() {
		return this.builder.dataServiceInterval;
	}

	@Override
	public int[] lowerSocBounds() {
		return this.builder.lowerSocBounds != null ? this.builder.lowerSocBounds : new int[]{10, 20};
	}

	@Override
	public int[] upperSocBounds() {
		return this.builder.upperSocBounds != null ? this.builder.upperSocBounds : new int[]{85, 95};
	}

	@Override
	public int maxPowerChangePerCycle() {
		return this.builder.maxPowerChangePerCycle != 0 ? this.builder.maxPowerChangePerCycle : 1000;
	}

	@Override
	public int maxChargePower() {
		return this.builder.maxChargePower != 0 ? this.builder.maxChargePower : 276_000;
	}

	@Override
	public int maxDischargePower() {
		return this.builder.maxDischargePower != 0 ? this.builder.maxDischargePower : 276_000;
	}

	@Override
	public double[] chargingEfficiencyKeys() {
		return this.builder.chargingEfficiencyKeys != null ? this.builder.chargingEfficiencyKeys : new double[]{0.0, 0.5, 1.0};
	}

	@Override
	public double[] chargingEfficiencyValues() {
		return this.builder.chargingEfficiencyValues != null ? this.builder.chargingEfficiencyValues : new double[]{0.85, 0.90, 0.85};
	}

	@Override
	public double[] dischargingEfficiencyKeys() {
		return this.builder.dischargingEfficiencyKeys != null ? this.builder.dischargingEfficiencyKeys : new double[]{0.0, 0.5, 1.0};
	}

	@Override
	public double[] dischargingEfficiencyValues() {
		return this.builder.dischargingEfficiencyValues != null ? this.builder.dischargingEfficiencyValues : new double[]{0.85, 0.90, 0.85};
	}

	@Override
	public double batteryChargingEfficiency() {
		return this.builder.batteryChargingEfficiency != 0 ? this.builder.batteryChargingEfficiency : 0.95;
	}

	@Override
	public double batteryDischargingEfficiency() {
		return this.builder.batteryDischargingEfficiency != 0 ? this.builder.batteryDischargingEfficiency : 0.95;
	}

	@Override
	public String alias() {
		return this.builder.id;
	}

	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public String webconsole_configurationFactory_nameHint() {
		return "Controller HybridController [{id}]";
	}

	@Override
	public Class<? extends java.lang.annotation.Annotation> annotationType() {
		return Config.class;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		MyConfig myConfig = (MyConfig) obj;
		return java.util.Objects.equals(this.builder.id, myConfig.builder.id);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(this.builder.id);
	}
}
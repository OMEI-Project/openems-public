package io.openems.edge.controller.ess.omei.hybrid;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.controller.ess.hybridess.controller.Config;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String mainId;
		private String supportId;
		private String meterId;
		private String energyPrediction;
		private String powerPrediction;
		private int defaultMinimumEnergy;
		private int maxGridPower;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}
		
		public Builder setMainId(String mainId) {
			this.mainId = mainId;
			return this;
		}
		
		public Builder setSupportId(String supportId) {
			this.supportId = supportId;
			return this;
		}
		
		public Builder setMeterId(String meterId) {
			this.meterId = meterId;
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

	public String mainId() {
		return this.builder.mainId;
	}
	
	@Override
	public String supportId() {
		return this.builder.supportId;
	}

	@Override
	public String meterId() {
		return this.builder.meterId;
	}

	@Override
	public String energyPrediction() {
		return this.builder.energyPrediction;
	}

	@Override
	public String powerPrediction() {
		return this.builder.powerPrediction;
	}

	@Override
	public int defaultMinimumEnergy() {
		return this.builder.defaultMinimumEnergy;
	}

	@Override
	public int maxGridPower() {
		return this.builder.maxGridPower;
	}

	
}
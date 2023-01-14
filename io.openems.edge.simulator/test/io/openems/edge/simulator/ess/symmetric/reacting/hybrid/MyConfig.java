package io.openems.edge.simulator.ess.symmetric.reacting.hybrid;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.simulator.ess.symmetric.hybrid.Config;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id = null;
		private Integer capacity = null;
		private Integer initialSoc = null;
		private GridMode gridMode = null;
		private int rampRate = 0;
		private int responseTime = 0;
		private int allowedDischargePower = 0;
		private int allowedChargePower = 0;

		private Builder() {

		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setCapacity(int capacity) {
			this.capacity = capacity;
			return this;
		}

		public Builder setInitialSoc(int initialSoc) {
			this.initialSoc = initialSoc;
			return this;
		}

		public Builder setGridMode(GridMode gridMode) {
			this.gridMode = gridMode;
			return this;
		}
		
		public Builder setRampRate(int rampRate) {
			this.rampRate = rampRate;
			return this;
		}
		
		public Builder setResponseTime(int responseTime) {
			this.responseTime = responseTime;
			return this;
		}
		
		public Builder setAllowedChargePower(int chargePower) {
			this.allowedChargePower = chargePower;
			return this;
		}
		
		public Builder setAllowedDischargePower(int dischargePower) {
			this.allowedDischargePower = dischargePower;
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
	public int capacity() {
		return this.builder.capacity;
	}

	@Override
	public int initialSoc() {
		return this.builder.initialSoc;
	}

	@Override
	public GridMode gridMode() {
		return this.builder.gridMode;
	}

	@Override
	public int rampRate() {
		return this.builder.rampRate;
	}

	@Override
	public long responseTime() {
		return this.builder.responseTime;
	}

	@Override
	public int allowedDischargePower() {
		return this.builder.allowedDischargePower;
	}

	@Override
	public int allowedChargePower() {
		return this.builder.allowedChargePower;
	}

}

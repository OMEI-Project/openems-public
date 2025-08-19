package io.openems.edge.simulator.ess.symmetric.reacting.hybrid;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.simulator.ess.symmetric.hybrid.Config;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private int[] higherSocBorder;
		private int[] lowerSocBorder;

		private String id = null;
		private Integer capacity = null;
		private Integer initialSoc = null;
		private GridMode gridMode = null;
		private int rampRate = 0;
		private int responseTime = 0;
		private int inactivityTime = 0;
		private int allowedDischargePower = 0;
		private int allowedChargePower = 0;
		private int maximumSoc = 90;
		private int minimumSoc = 10;
		
		private double[] chargingEfficiencyValues = {1.0};
        private double[] chargingEfficiencyKeys = {1.0};
        
        private double[] dischargingEfficiencyValues = {1.0};
        private double[] dischargingEfficiencyKeys = {1.0};
        
        private double batteryChargingEfficiency = 1.0;
        private double batteryDischargingEfficiency = 1.0;      

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

		public Builder setInactivityTime(int inactivityTime) {
			this.inactivityTime = inactivityTime;
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

		public Builder setLowerSocBorder(int[] socBorder) {
			this.lowerSocBorder = socBorder;
			return this;
		}

		public Builder setHigherSocBorder(int[] socBorder) {
			this.higherSocBorder = socBorder;
            return this;
        }
        public Builder setChargingEfficencyValues(double[] efficencyValues) {
                this.chargingEfficiencyValues = efficencyValues;
                return this;
        }
        public Builder setChargingEfficencyKeys(double[] efficencyKeys) {
                this.chargingEfficiencyKeys = efficencyKeys;
                return this;
        }
        
        public Builder setDischargingEfficencyValues(double[] efficencyValues) {
                this.dischargingEfficiencyValues = efficencyValues;
                return this;
        }
        public Builder setDischargingEfficencyKeys(double[] efficencyKeys) {
                this.dischargingEfficiencyKeys = efficencyKeys;
                return this;
        }
        
        public Builder setBatteryChargingEfficency(double efficency) {
                this.batteryChargingEfficiency = efficency;
                return this;
        }
        
        public Builder setBatteryDischargingEfficency(double efficency) {
                this.batteryDischargingEfficiency = efficency;
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
	public long inactivityTime() {
		return this.builder.inactivityTime;
	}

	@Override
	public int minimumSoc() {
		return this.builder.minimumSoc;
	}

	@Override
	public int maximumSoc() {
		return this.builder.maximumSoc;
	}

	@Override
	public int allowedDischargePower() {
		return this.builder.allowedDischargePower;
	}

	@Override
	public int allowedChargePower() {
		return this.builder.allowedChargePower;
	}

	@Override
    public String[] chargingEfficiencyKeys() {
            return null;
    }
	
    @Override
    public String[] chargingEfficiencyValues() {
            return null;
    }
    
    @Override
    public String[] dischargingEfficiencyKeys() {
            return null;
    }
    
    @Override
    public String[] dischargingEfficiencyValues() {
            return null;
    }
    
    @Override
    public double batteryChargingEfficiency() {
            return this.builder.batteryChargingEfficiency;
    }
    
    @Override
    public double batteryDischargingEfficiency() {
            return this.builder.batteryDischargingEfficiency;
    }
    
    @Override
	public String[] higherSocBorder() {
		return null;
	}

	@Override
	public String[] lowerSocBorder() {
		return null;
	}

}

package io.openems.edge.simulator.ess.symmetric.hybrid;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import io.openems.common.exceptions.InvalidValueException;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.simulator.ess.symmetric.reacting.EssSymmetric;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(name = "Simulator.EssSymmetric.Reacting.Omei", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
})
public class EssSymmetricHybrid extends AbstractOpenemsComponent 
	implements ManagedSymmetricEssHybrid, ManagedSymmetricEss, SymmetricEss, OpenemsComponent, TimedataProvider, EventHandler, StartStoppable, ModbusSlave {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		;
		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}
	
	private Config config;
	
	/**
	 * Maximum amount of power change from last step to current.
	 */
	private int rampRate;
	
	/**
	 * Time from power Requested to first power supplied in [s].
	 */
	private long responseTime;
	
	private Instant timestampStartup;
	
	private Instant inactivityTimestamp;
	
	private OperatingStatus operatingStatus;
	
	/**
	 * Current Energy in the battery [Wms], based on SoC.
	 */
	private long energy = 0;
	
	private Instant lastTimestamp = null;

	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);
	
	private final static int POWER_PRECISION = 1;

	// TODO: After this duration ESS become inactive again.
	private static final long ACTIVITY_TIME_OUT = Long.MAX_VALUE;
	
	private boolean ready;

	private int maxChargePower;

	private int maxDischargePower;
	
	@Reference
	private Power power;

	@Reference
	protected ConfigurationAdmin cm;

	@Reference
	protected ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;
	
	public EssSymmetricHybrid(){
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricEss.ChannelId.values(), //
				ManagedSymmetricEss.ChannelId.values(), //
				ManagedSymmetricEssHybrid.ChannelId.values(),
				StartStoppable.ChannelId.values(), //
				ChannelId.values() //
		);
	}
	
	@Activate
	void activate(ComponentContext context, Config config) throws IOException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this.energy = (long) ((double) config.capacity() /* [Wh] */ * 3600 /* [Wsec] */ * 1000 /* [Wmsec] */
				/ 100 * this.config.initialSoc() /* [current SoC] */);
		this._setSoc(config.initialSoc());
		this._setMaxApparentPower(config.allowedDischargePower());
		this.maxChargePower = (-Math.abs(config.allowedChargePower()));
		this.maxDischargePower = (config.allowedDischargePower());
		this._setGridMode(config.gridMode());
		this._setCapacity(config.capacity());
		this.rampRate = config.rampRate();
		this.responseTime = Duration.of(config.responseTime(), ChronoUnit.MILLIS).toSeconds();
		this.ready = responseTime == 0;
	}
	
	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}
	
	@Override
	public void handleEvent(Event event) {
		Integer activePower = this.getActivePower().orElse(0);
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			
			if(responseTimeElapsed()) {
				ready = true;
				timestampStartup = null;
				inactivityTimestamp = null;
			}
			this.calculateEnergy();
			this.calculatePossibleChargePower();
			this.calculatePossibleDischargePower();;
			
			if(activePower != 0) {
				if(activePower > 0) {
					this.operatingStatus = OperatingStatus.CHARGING;
					this.calculateChargeTime(activePower);
				} else {
					this.operatingStatus = OperatingStatus.DISCHARGING;
					this.calculateDischargeTime(activePower);
				}
				inactivityTimestamp = null;
			} else {
				if(inactivityTimestamp == null) {
					inactivityTimestamp = Instant.now(componentManager.getClock());
				}
				if(inactivityTimeElapsed()) {
					inactivityTimestamp = null;
					ready = false;
				}
			}
			
			break;
		}
	}
	
	/*
	 * Gets called by Solver.java after power constraints have been solved. 
	 */
	@Override
	public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
		
		/*
		 * calculate State of charge
		 */
		Instant now = Instant.now(this.componentManager.getClock());
		final int soc = calculateSoc(now);
		this._setSoc(soc);
		this.lastTimestamp = now;
		
		/*
		 * Apply Active/Reactive power to simulated channels
		 */
		if ((soc == 0 && activePower > 0) 
				|| (soc == 100 && activePower < 0)) {
			activePower = 0;
		}
		this._setActivePower(activePower);
		
		if ((soc == 0 && reactivePower > 0) 
				|| (soc == 100 && reactivePower < 0) ) {
			reactivePower = 0;
		}
		this._setReactivePower(reactivePower);
	}
	
	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				SymmetricEss.getModbusSlaveNatureTable(accessMode), //
				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode), //
				StartStoppable.getModbusSlaveNatureTable(accessMode), //
				ModbusSlaveNatureTable.of(EssSymmetric.class, accessMode, 100) //
				.build());
	}

	@Override
	public void setStartStop(StartStop value) throws OpenemsNamedException {
		this._setStartStop(value);
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public Power getPower() {
		return this.power;
	}

	@Override
	public int getPowerPrecision() {
		return POWER_PRECISION;
	}
	
	@Override
	public String debugLog() {
		return "SoC:" + this.getSoc().asString() //
				+ "|L:" + this.getActivePower().asString() //
				+ "|Allowed:" + this.getAllowedChargePower().asStringWithoutUnit() + ";"
				+ this.getAllowedDischargePower().asString();
	}

	@Override
	public int filterPower(int targetPower) {
		int filteredPower = targetPower;
		int upperLimit = 0;
		int lowerLimit = 0;

		if(!ready && targetPower != 0) {
			beginStartTimer();
			filteredPower = 0;
		} else if (targetPower >= 0){
			// Discharging

			upperLimit = this.getUpperPossibleDischargePower().orElse(0);
			lowerLimit = this.getLowerPossibleDischargePower().orElse(0);
		} else {
			// Charging

			upperLimit = this.getUpperPossibleChargePower().orElse(0);
			lowerLimit = this.getLowerPossibleChargePower().orElse(0);
		}

		if(targetPower > upperLimit) {
			filteredPower = upperLimit;
		} else if(targetPower < lowerLimit) {
			filteredPower = lowerLimit;
		}

		return filteredPower;
	}

	private void calculatePossibleChargePower() {
		int lowerChargePower = 0;
		int upperChargePower = 0;
		if(ready) {
			int currentPower = this.getActivePower().orElse(0);

			// TODO: Assumes to be called every cycle and cycle duration = 1s. ramp rate should be multiplied with time since last calc.
			// TODO: Down  Ramp Rate
			lowerChargePower = Math.min(Math.max(currentPower - rampRate, maxChargePower),0);
			upperChargePower = Math.min(currentPower + rampRate, 0);
		}
		this._setAllowedChargePower(lowerChargePower);
		this._setLowerPossibleChargePower(lowerChargePower);
		this._setUpperPossibleChargePower(upperChargePower);
	}

	private void calculatePossibleDischargePower() {
		int lowerDischargePower = 0;
		int upperDischargePower = 0;
		if(ready) {
			int currentPower = this.getActivePower().orElse(0);

			// TODO: Assumes to be called every cycle and cycle duration = 1s. ramp rate should be multiplied with time since last calc.
			// TODO: Down  Ramp Rate
			lowerDischargePower = Math.max(currentPower - rampRate, 0);
			upperDischargePower = Math.max(Math.min(currentPower + rampRate, maxDischargePower),0);
		}
		this._setAllowedDischargePower(upperDischargePower);
		this._setLowerPossibleDischargePower(lowerDischargePower);
		this._setUpperPossibleDischargePower(upperDischargePower);
	}
	
	private boolean responseTimeElapsed() {
	
		return timestampStartup != null && Duration.between(timestampStartup, Instant.now(componentManager.getClock())).toSeconds() >= responseTime;
	}
	
	private boolean inactivityTimeElapsed() {
		return Duration.between(inactivityTimestamp, Instant.now(componentManager.getClock())).toSeconds() >= ACTIVITY_TIME_OUT;
	}
	
	private void beginStartTimer() {
		if(timestampStartup == null) {
			this.timestampStartup = Instant.now(componentManager.getClock());
		}
	}

	private void calculateChargeTime(int power) {
		if(power > 0) {
			throw new IllegalArgumentException("Power for charging must be negative");
		}
		int unusedCapacity = this.getCapacity().orElse(0) - this.getActivePower().orElse(0);
		unusedCapacity *= 3600; // to Ws
		long chargeSeconds = unusedCapacity / -power;

		// What to do with it? Write to channel?
		Duration chargeTime = Duration.of(chargeSeconds, ChronoUnit.SECONDS);
	}

	private void calculateDischargeTime(int power) {
		if(power < 0) {
			throw new IllegalArgumentException("Power for charging must be positive");
		}

		int unusedCapacity = this.getCapacity().orElse(0) - this.getActivePower().orElse(0);
		unusedCapacity *= 3600; // to Ws
		long chargeSeconds = unusedCapacity / power;

		// What to do with it? Write to channel?
		Duration chargeTime = Duration.of(chargeSeconds, ChronoUnit.SECONDS);

	}
	
	/**
	 * Calculate the Energy values from ActivePower.
	 * 
	 * TODO {@link CalculateEnergyFromPower#update(Integer)} does not use 
	 * the shared clock e.g. {@code Instant.now()} instead of Instant.now(componentManager.getClock()).
	 * This could produce errors during simulation and testing on timeleaps, faster clock etc.
	 */
	private void calculateEnergy() {
		// Calculate Energy
		Integer activePower = this.getActivePower().get();
		if (activePower == null) {
			// Not available
			this.calculateChargeEnergy.update(null);
			this.calculateDischargeEnergy.update(null);
		} else if (activePower > 0) {
			// Buy-From-Grid
			this.calculateChargeEnergy.update(0);
			this.calculateDischargeEnergy.update(activePower);
		} else {
			// Sell-To-Grid
			this.calculateChargeEnergy.update(activePower * -1);
			this.calculateDischargeEnergy.update(0);
		}
	}
	
	private int calculateSoc(Instant now) {
		int soc = this.config.initialSoc();
		
		// Check if this is not the initial run
		if (this.lastTimestamp != null) {
			// calculate duration since last value
			long duration /* [msec] */ = Duration.between(this.lastTimestamp, now).toMillis();

			// calculate energy since last run in [Wh]
			long energy /* [Wmsec] */ = this.getActivePower().orElse(0) /* [W] */ * duration /* [msec] */;

			// Adding the energy to the initial energy.
			this.energy -= energy;

			double calculatedSoc = this.energy //
					/ (this.config.capacity() * 3600. /* [Wsec] */ * 1000 /* [Wmsec] */) //
					* 100 /* [SoC] */;

			if (calculatedSoc > 100) {
				soc = 100;
			} else if (calculatedSoc < 0) {
				soc = 0;
			} else {
				soc = (int) Math.round(calculatedSoc);
			}
		}
		return soc;
	}
}

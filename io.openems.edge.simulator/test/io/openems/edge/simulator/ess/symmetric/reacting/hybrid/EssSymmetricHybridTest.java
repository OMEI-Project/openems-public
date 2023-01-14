package io.openems.edge.simulator.ess.symmetric.reacting.hybrid;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.TimeLeapClock;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.test.DummyPower;
import io.openems.edge.simulator.ess.symmetric.hybrid.EssSymmetricHybrid;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;


public class EssSymmetricHybridTest {
	
	private static final String ESS_ID = "ess0";
	private static final int CAPACITY = 400_000;
	private static final int MAX_APPARENT_POWER = 100_000;
	private static final int SOC = 50;
	private static final int RAMP_RATE = 40_000;
	private static final int RESPONSE_TIME = 2000;
	private static final int CHARGE_POWER = -100_000;
	private static final int DISCHARGE_POWER = 100_000;

	private static final ChannelAddress ESS_SOC = new ChannelAddress(ESS_ID, SymmetricEss.ChannelId.SOC.id());
	private static final ChannelAddress ESS_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(ESS_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	private static final ChannelAddress ESS_ACTIVE_POWER = new ChannelAddress(ESS_ID,
			SymmetricEss.ChannelId.ACTIVE_POWER.id());
	
	private static final ChannelAddress ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(ESS_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(ESS_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(ESS_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(ESS_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());

	private static final ChannelAddress ESS_GET_ALLOWED_CHARGE_POWER = new ChannelAddress(ESS_ID, ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER.id());
	private static final ChannelAddress ESS_GET_ALLOWED_DISCHARGE_POWER = new ChannelAddress(ESS_ID, ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER.id());
	final TimeLeapClock clock = new TimeLeapClock(Instant.now(), ZoneOffset.UTC);
	
	
	private ManagedSymmetricEssHybridTest setup(String ess_id, int capacity, int maxApparentPower, int SoC, GridMode gridMode,
			int rampRate, int responseTime, int chargePower, int dischargePower) throws Exception {
		return new ManagedSymmetricEssHybridTest(new EssSymmetricHybrid()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("power", new DummyPower()) //
				.activate(MyConfig.create() //
						.setId(ess_id) //
						.setCapacity(capacity) //
						.setInitialSoc(SoC) //
						.setGridMode(gridMode) //
						.setRampRate(rampRate)
						.setResponseTime(responseTime)
						.setAllowedChargePower(chargePower)
						.setAllowedDischargePower(dischargePower)
						.build());
	}
	
	private ManagedSymmetricEssHybridTest setup() throws Exception {
		return this.setup(ESS_ID, CAPACITY, MAX_APPARENT_POWER, SOC, GridMode.ON_GRID, RAMP_RATE, RESPONSE_TIME, CHARGE_POWER, DISCHARGE_POWER);
	}
	
	/*
	 * Test if logic for no response time works.
	 * ESS should be able to charge right away. 
	 */
	@Test
	public void noResponseTime() throws Exception {
		ManagedSymmetricEssHybridTest testEss = setup(ESS_ID,
				CAPACITY,
				MAX_APPARENT_POWER,
				SOC, GridMode.ON_GRID,
				RAMP_RATE,
				0, // No RESPONSE TIME
				CHARGE_POWER,
				DISCHARGE_POWER);
		testEss.next(new TestCase()
				.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
				.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, RAMP_RATE)
				.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -RAMP_RATE)
				.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0));
	}
	
	@Test
	public void responseTime() throws Exception {
		
		ManagedSymmetricEssHybridTest testEss = setup(ESS_ID,
				CAPACITY,
				MAX_APPARENT_POWER,
				SOC, GridMode.ON_GRID,
				RAMP_RATE,
				1000*60*15, // RESPONSE TIME = 15min
				CHARGE_POWER,
				DISCHARGE_POWER);
		testEss.next(new TestCase() // Response time not elapsed.
				    .output(ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 0)
					.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
					.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
					.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, 0));
		
		testEss.getSut().filterPower(1); // Begin startup time.
		testEss.next(new TestCase() // Response time elapsed
					.timeleap(clock, 15, ChronoUnit.MINUTES) // wait for response time to elapse.
					.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
					.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, RAMP_RATE)
					.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -RAMP_RATE)
					.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0));
	}

	@Test
	public void chargeLimits() throws Exception {
		ManagedSymmetricEssHybridTest testEss = setup();
		testEss.getSut().filterPower(1); // Begin startup time.
		testEss.next(new TestCase()
						.timeleap(clock, RESPONSE_TIME + 1, ChronoUnit.MILLIS)
						.input(ESS_ACTIVE_POWER, -10_000)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -RAMP_RATE - 10_000)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT,0)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 30_000)
						.output(ESS_GET_ALLOWED_CHARGE_POWER, -RAMP_RATE - 10_000))
				.next(new TestCase()
						.input(ESS_ACTIVE_POWER, -RAMP_RATE-10_000)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, -10_000)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -2*RAMP_RATE - 10_000)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 0)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT,0)
						.output(ESS_GET_ALLOWED_CHARGE_POWER, -2*RAMP_RATE - 10_000))
				.next(new TestCase()
						.input(ESS_ACTIVE_POWER, CHARGE_POWER + 10_000)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, CHARGE_POWER + 10_000 + RAMP_RATE)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, CHARGE_POWER)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT,0)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT,0)
						.output(ESS_GET_ALLOWED_CHARGE_POWER, CHARGE_POWER));
	}

	@Test
	public void dischargeLimits() throws Exception {

		ManagedSymmetricEssHybridTest testEss = setup();
		testEss.getSut().filterPower(1); // Begin startup
		testEss.next(new TestCase()
						.timeleap(clock, RESPONSE_TIME + 1, ChronoUnit.MILLIS)
						.input(ESS_ACTIVE_POWER, 10_000)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -30_000)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, RAMP_RATE + 10_000)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.output(ESS_GET_ALLOWED_DISCHARGE_POWER, RAMP_RATE + 10_000))
				.next(new TestCase()
						.input(ESS_ACTIVE_POWER,RAMP_RATE + 10_000)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, 0)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 10_000)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 2*RAMP_RATE + 10_000)
						.output(ESS_GET_ALLOWED_DISCHARGE_POWER,2*RAMP_RATE + 10_000))
				.next(new TestCase()
						.input(ESS_ACTIVE_POWER, DISCHARGE_POWER - 10_000)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, 0)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, DISCHARGE_POWER)
						.output(ESS_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, DISCHARGE_POWER - RAMP_RATE - 10_000)
						.output(ESS_GET_ALLOWED_DISCHARGE_POWER, DISCHARGE_POWER));
	}
	
	@Test
	public void rampingUp() {
		// Specify ramping behavior and common timestamp first.
	}
}

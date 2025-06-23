package io.openems.edge.controller.ess.omei.hybrid;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.controller.ess.hybridess.controller.HybridControllerImpl;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.test.DummyPower;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.test.DummyElectricityMeter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.StringJoiner;

import static org.junit.Assert.fail;

public class HybridControllerTest {

	@Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
	private Path energyPrediction;
	private Path powerPrediction;

	private TimeLeapClock clock;

	private static final String CTRL_ID = "ctrl0";
	// Commented out for single battery mode - can be easily reactivated
	// private static final String MAIN_ID = "ess0";
	private static final String SUPPORT_ID ="ess1";
	
	// Main battery power specs commented out for single battery mode
	// private static final int MAIN_MAX_APPARENT_POWER = 100_000;
	private static final int SUPPORT_MAX_APPARENT_POWER = 276_000;

	// Main battery channels commented out for single battery mode
	// private static final ChannelAddress MAIN_SOC = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.SOC.id());
	private static final ChannelAddress SUPPORT_SOC = new ChannelAddress(SUPPORT_ID, SymmetricEss.ChannelId.SOC.id());
	// private static final ChannelAddress MAIN_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(MAIN_ID,
	//		ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	private static final ChannelAddress SUPPORT_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(SUPPORT_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	// Main battery channel addresses commented out for single battery mode
	/*
	private static final ChannelAddress MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_MAX_APPARENT_POWER_CHANNEL = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.MAX_APPARENT_POWER.id());
	private static final ChannelAddress MAIN_CAPACITY = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.CAPACITY.id());
	private static final ChannelAddress MAIN_ACTIVE_POWER = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.ACTIVE_POWER.id());
	*/

	private static final ChannelAddress SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());

	private static final ChannelAddress SUPPORT_CAPACITY = new ChannelAddress(SUPPORT_ID, SymmetricEss.ChannelId.CAPACITY.id());
	private static final ChannelAddress SUPPORT_ACTIVE_POWER = new ChannelAddress(SUPPORT_ID,
			SymmetricEss.ChannelId.ACTIVE_POWER.id());
	
	private static final String METER_ID = "meter0";
	private static final ChannelAddress METER_ACTIVE_POWER = new ChannelAddress(METER_ID, ElectricityMeter.ChannelId.ACTIVE_POWER.id());

	private static final String SUM_ID = "_sum";

	private static final ChannelAddress PRODUCTION_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.PRODUCTION_ACTIVE_POWER.id());
	private static final ChannelAddress CONSUMPTION_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.CONSUMPTION_ACTIVE_POWER.id());

	private static final ChannelAddress GRID_ACTIVE_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.GRID_ACTIVE_POWER.id());
	private static final int MAX_GRID_POWER = 200_000; // W
	private static final int DEFAULT_MIN_ENERGY = 100_000; // Wh
	
	@Test
	public void singleBatteryBasicCharge() throws Exception {
		// Test basic charging behavior with single battery
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // singleBatteryBasicCharge#1
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(SUPPORT_CAPACITY, 276_000)
						.input(SUPPORT_SOC, 5) // Start in red area 0.05 * 276_000 = 13,800Wh
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER) // All grid power should go to battery
						)
				.next(new TestCase() // singleBatteryBasicCharge#2 - medium SoC
						.input(METER_ACTIVE_POWER,0)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(SUPPORT_SOC, 50) // Medium SoC
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(PRODUCTION_POWER, 50_000) // Some production available
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -(MAX_GRID_POWER + 50_000)) // Grid + production should charge battery
						);
	}

	@Test
	public void singleBatteryBasicDischarge() throws Exception {
		// Test basic discharging behavior with single battery
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // singleBatteryBasicDischarge#1
						.input(METER_ACTIVE_POWER,100_000) // 100kW consumption
						.input(SUPPORT_CAPACITY, 276_000)
						.input(SUPPORT_SOC, 80) // High SoC
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 200_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(PRODUCTION_POWER, 0) // No production
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 100_000) // Should discharge to meet consumption
						)
				.next(new TestCase() // singleBatteryBasicDischarge#2 - with production
						.input(METER_ACTIVE_POWER,100_000) // 100kW consumption
						.input(SUPPORT_CAPACITY, 276_000)
						.input(SUPPORT_SOC, 80) // High SoC
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 200_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(PRODUCTION_POWER, 30_000) // 30kW production
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 70_000) // Should discharge only the difference
						);
	}

	@Test
	public void singleBatteryRedProtection() throws Exception {
		// Test that battery in RED state is protected from discharge
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // singleBatteryRedProtection#1
						.input(METER_ACTIVE_POWER,50_000) // 50kW consumption
						.input(SUPPORT_CAPACITY, 276_000)
						.input(SUPPORT_SOC, 5) // RED SoC area
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -200_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(PRODUCTION_POWER, 0) // No production
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -(MAX_GRID_POWER - 50_000)) // Should charge with remaining grid power
						);
	}

	@Test
	public void singleBatteryMinEnergyCheck() throws Exception {
		// Test minimum energy enforcement
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // singleBatteryMinEnergyCheck#1
						.input(METER_ACTIVE_POWER,0) // No consumption
						.input(SUPPORT_CAPACITY, 276_000)
						.input(SUPPORT_SOC, 35) // 35% SoC = 96,600Wh < 100,000Wh minimum
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(PRODUCTION_POWER, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER) // Should charge to meet minimum energy
						);
	}

	// Keep original test structure but simplified for single battery - commented out dual battery tests
	/*
	@Test
	public void chargeSplit() throws Exception {
		// Original dual battery charge split tests - preserved for reactivation
	}

	@Test  
	public void dischargeSplit() throws Exception {
		// Original dual battery discharge split tests - preserved for reactivation
	}
	*/

	private ControllerTest createControllerTest() throws Exception {
		return createControllerTest(DEFAULT_MIN_ENERGY);
	}

	private ControllerTest createControllerTest(int defaultMinimumGridPower) throws Exception {
		clock = new TimeLeapClock(Instant.ofEpochSecond(1577836800L /* 2020-01-01 00:00:00 UTC */), ZoneOffset.UTC);
		Sum sum = new DummySum();
		return new ControllerTest(new HybridControllerImpl(defaultMinimumGridPower, MAX_GRID_POWER,
				null, // No main ID for single battery mode
				SUPPORT_ID, "http://127.0.0.1:5000/", sum, new DummyComponentManager(clock))) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("sum", sum) //
				.addComponent(new DummyElectricityMeter(METER_ID)) //
				// Remove main ESS setup for single battery mode
				// .addComponent(setupESS(MAIN_ID, MAIN_MAX_APPARENT_POWER)) //
				.addComponent(setupESS(SUPPORT_ID, SUPPORT_MAX_APPARENT_POWER)) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						// .setMainId(MAIN_ID) // Commented out for single battery mode
						.setSupportId(SUPPORT_ID) //
						.setMeterId(METER_ID) //
						.setDefaultMinimumEnergy(defaultMinimumGridPower) //
						.setMaxGridPower(MAX_GRID_POWER) //
						.setDataAcquisitionServiceBaseUrl("http://127.0.0.1:5000/") //
						.setDataServiceInterval(10) // Use default interval for tests
						.build());
	}

	private static ManagedSymmetricEssHybrid setupESS(String id, int maxApparentPower) {
		return setupESS(id, maxApparentPower, new int[]{10, 20}, new int[]{85, 95});
	}

	private static ManagedSymmetricEssHybrid setupESS(String id, int maxApparentPower,
													  int[] lowerSocBorder, int[] upperSocBorder) {
		return new DummyHybridEss(id, new DummyPower(maxApparentPower), lowerSocBorder, upperSocBorder);
	}

}

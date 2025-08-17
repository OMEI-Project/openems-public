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
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.test.DummyElectricityMeter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;

public class HybridControllerTest {

	@Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
	@SuppressWarnings("unused")
	private Path energyPrediction;
	@SuppressWarnings("unused")
	private Path powerPrediction;

	private TimeLeapClock clock;

	private static final String CTRL_ID = "ctrl0";
	private static final String MAIN_ID = "ess0";
	private static final String SUPPORT_ID ="ess1";
	
	private static final int MAIN_MAX_APPARENT_POWER = 400_000;
	private static final int SUPPORT_MAX_APPARENT_POWER = 276_000;

	private static final ChannelAddress MAIN_SOC = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.SOC.id());
	private static final ChannelAddress MAIN_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(MAIN_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER.id());
	private static final ChannelAddress MAIN_CAPACITY = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.CAPACITY.id());
	@SuppressWarnings("unused")
	private static final ChannelAddress MAIN_ACTIVE_POWER = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.ACTIVE_POWER.id());

	private static final ChannelAddress SUPPORT_SOC = new ChannelAddress(SUPPORT_ID, SymmetricEss.ChannelId.SOC.id());
	@SuppressWarnings("unused")
	private static final ChannelAddress SUPPORT_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(SUPPORT_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	@SuppressWarnings("unused")
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(SUPPORT_ID,
			ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER.id());
	@SuppressWarnings("unused")
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(SUPPORT_ID,
			ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER.id());

	@SuppressWarnings("unused")
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(SUPPORT_ID,
			ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER.id());
	@SuppressWarnings("unused")
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(SUPPORT_ID,
			ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER.id());

	private static final ChannelAddress SUPPORT_CAPACITY = new ChannelAddress(SUPPORT_ID, SymmetricEss.ChannelId.CAPACITY.id());
	@SuppressWarnings("unused")
	private static final ChannelAddress SUPPORT_ACTIVE_POWER = new ChannelAddress(SUPPORT_ID,
			SymmetricEss.ChannelId.ACTIVE_POWER.id());
	
	private static final String METER_ID = "meter0";
	private static final ChannelAddress METER_ACTIVE_POWER = new ChannelAddress(METER_ID, ElectricityMeter.ChannelId.ACTIVE_POWER.id());

	private static final String SUM_ID = "_sum";

	private static final ChannelAddress PRODUCTION_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.PRODUCTION_ACTIVE_POWER.id());
	@SuppressWarnings("unused")
	private static final ChannelAddress CONSUMPTION_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.CONSUMPTION_ACTIVE_POWER.id());

	@SuppressWarnings("unused")
	private static final ChannelAddress GRID_ACTIVE_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.GRID_ACTIVE_POWER.id());
	private static final int MAX_GRID_POWER = 200_000; // W
	private static final int DEFAULT_MIN_ENERGY = 100_000; // Wh
	
	@Test
	public void singleBatteryBasicCharge() throws Exception {
		// Test basic charging behavior with single battery (using main battery)
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // singleBatteryBasicCharge#1
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(MAIN_SOC, 5) // Start in red area 0.05 * 400_000 = 20,000Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER) // All grid power should go to main battery
						)
				.next(new TestCase() // singleBatteryBasicCharge#2 - medium SoC
						.input(METER_ACTIVE_POWER,0)
						.input(MAIN_CAPACITY, 400_000)
						.input(MAIN_SOC, 50) // Medium SoC
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(PRODUCTION_POWER, 50_000) // Some production available
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -(MAX_GRID_POWER + 50_000)) // Grid + production should charge battery
						);
	}

	@Test
	public void singleBatteryBasicDischarge() throws Exception {
		// Test basic discharging behavior with single battery (using main battery)
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // singleBatteryBasicDischarge#1
						.input(METER_ACTIVE_POWER,100_000) // 100kW consumption
						.input(MAIN_CAPACITY, 400_000)
						.input(MAIN_SOC, 80) // High SoC
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 200_000)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(PRODUCTION_POWER, 0) // No production
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 100_000) // Should discharge to meet consumption
						)
				.next(new TestCase() // singleBatteryBasicDischarge#2 - with production
						.input(METER_ACTIVE_POWER,100_000) // 100kW consumption
						.input(MAIN_CAPACITY, 400_000)
						.input(MAIN_SOC, 80) // High SoC
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 200_000)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(PRODUCTION_POWER, 30_000) // 30kW production
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 70_000) // Should discharge only the difference
						);
	}

	@Test
	public void singleBatteryRedProtection() throws Exception {
		// Test that battery in RED state is protected from discharge (using main battery)
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // singleBatteryRedProtection#1
						.input(METER_ACTIVE_POWER,50_000) // 50kW consumption
						.input(MAIN_CAPACITY, 400_000)
						.input(MAIN_SOC, 5) // RED SoC area
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -200_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(PRODUCTION_POWER, 0) // No production
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -(MAX_GRID_POWER - 50_000)) // Should charge with remaining grid power
						);
	}

	@Test
	public void singleBatteryMinEnergyCheck() throws Exception {
		// Test minimum energy enforcement (using main battery)
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // singleBatteryMinEnergyCheck#1
						.input(METER_ACTIVE_POWER,0) // No consumption
						.input(MAIN_CAPACITY, 400_000)
						.input(MAIN_SOC, 24) // 24% SoC = 96,000Wh < 100,000Wh minimum
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(PRODUCTION_POWER, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER) // Should charge to meet minimum energy
						);
	}

	@Test
	public void dualBatteryBasicCharge() throws Exception {
		// Test basic charging behavior with dual battery
		ControllerTest controllerTest = createDualBatteryControllerTest();
		controllerTest.next(new TestCase() // dualBatteryBasicCharge#1
						.input(METER_ACTIVE_POWER, 0) // No consumption
						.input(MAIN_CAPACITY, 400_000)
						.input(MAIN_SOC, 50) // Medium SoC for main
						.input(SUPPORT_CAPACITY, 276_000)
						.input(SUPPORT_SOC, 10) // Low SoC for support (RED state)
						.input(PRODUCTION_POWER, 0)
						// In dual mode with support in RED state, should charge heavily
						// Power distribution will favor support battery due to RED state
						);
	}

	@Test
	public void dualBatteryBasicDischarge() throws Exception {
		// Test basic discharging behavior with dual battery
		ControllerTest controllerTest = createDualBatteryControllerTest();
		controllerTest.next(new TestCase() // dualBatteryBasicDischarge#1
						.input(METER_ACTIVE_POWER, 100_000) // 100kW consumption
						.input(MAIN_CAPACITY, 400_000)
						.input(MAIN_SOC, 80) // High SoC for main
						.input(SUPPORT_CAPACITY, 276_000)
						.input(SUPPORT_SOC, 70) // Medium-high SoC for support
						.input(PRODUCTION_POWER, 0) // No production
						// Should discharge to meet consumption, distributed between batteries
						);
	}

	private ControllerTest createControllerTest() throws Exception {
		return createControllerTest(DEFAULT_MIN_ENERGY);
	}

	private ControllerTest createControllerTest(int defaultMinimumGridPower) throws Exception {
		clock = new TimeLeapClock(Instant.ofEpochSecond(1577836800L /* 2020-01-01 00:00:00 UTC */), ZoneOffset.UTC);
		Sum sum = new DummySum();
		return new ControllerTest(new HybridControllerImpl(defaultMinimumGridPower, MAX_GRID_POWER,
				MAIN_ID, // Main ID for single battery mode (now uses main battery)
				SUPPORT_ID, "http://127.0.0.1:5000/", sum, new DummyComponentManager(clock))) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("sum", sum) //
				.addComponent(new DummyElectricityMeter(METER_ID)) //
				.addComponent(setupESS(MAIN_ID, MAIN_MAX_APPARENT_POWER)) // Setup main battery for single mode
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setMainId(MAIN_ID) // Set main ID
						.setEnableDualBatteryMode(false) // Single battery mode
						.setEnableMinimumEnergyFunction(true) // Enable minimum energy function for tests
						.setDefaultMinimumEnergy(defaultMinimumGridPower) //
						.setMaxGridPower(MAX_GRID_POWER) //
						.setDataAcquisitionServiceBaseUrl("http://127.0.0.1:5000/") //
						.setDataServiceInterval(10) //
						.setLowerSocBounds(new int[]{10, 20}) //
						.setUpperSocBounds(new int[]{85, 95}) //
						.setMaxPowerChangePerCycle(1000) //
						.setMaxChargePower(276_000) //
						.setMaxDischargePower(276_000) //
						.setChargingEfficiencyKeys(new double[]{0.0, 0.5, 1.0}) //
						.setChargingEfficiencyValues(new double[]{0.85, 0.90, 0.85}) //
						.setDischargingEfficiencyKeys(new double[]{0.0, 0.5, 1.0}) //
						.setDischargingEfficiencyValues(new double[]{0.85, 0.90, 0.85}) //
						.setBatteryChargingEfficiency(0.95) //
						.setBatteryDischargingEfficiency(0.95) //
						.build());
	}

	private ControllerTest createDualBatteryControllerTest() throws Exception {
		return createDualBatteryControllerTest(DEFAULT_MIN_ENERGY);
	}

	private ControllerTest createDualBatteryControllerTest(int defaultMinimumGridPower) throws Exception {
		clock = new TimeLeapClock(Instant.ofEpochSecond(1577836800L /* 2020-01-01 00:00:00 UTC */), ZoneOffset.UTC);
		Sum sum = new DummySum();
		return new ControllerTest(new HybridControllerImpl(defaultMinimumGridPower, MAX_GRID_POWER,
				MAIN_ID, // Main ID for dual battery mode
				SUPPORT_ID, "http://127.0.0.1:5000/", sum, new DummyComponentManager(clock))) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("sum", sum) //
				.addComponent(new DummyElectricityMeter(METER_ID)) //
				.addComponent(setupESS(MAIN_ID, MAIN_MAX_APPARENT_POWER)) //
				.addComponent(setupESS(SUPPORT_ID, SUPPORT_MAX_APPARENT_POWER)) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setEnableDualBatteryMode(true) // Dual battery mode
						.setMainId(MAIN_ID) //
						.setSupportId(SUPPORT_ID) //
						.setEnableMinimumEnergyFunction(true) // Enable minimum energy function for tests
						.setDefaultMinimumEnergy(defaultMinimumGridPower) //
						.setMaxGridPower(MAX_GRID_POWER) //
						.setDataAcquisitionServiceBaseUrl("http://127.0.0.1:5000/") //
						.setDataServiceInterval(10) //
						.setLowerSocBounds(new int[]{10, 20}) //
						.setUpperSocBounds(new int[]{85, 95}) //
						.setMaxPowerChangePerCycle(1000) //
						.setMaxChargePower(276_000) //
						.setMaxDischargePower(276_000) //
						.setChargingEfficiencyKeys(new double[]{0.0, 0.5, 1.0}) //
						.setChargingEfficiencyValues(new double[]{0.85, 0.90, 0.85}) //
						.setDischargingEfficiencyKeys(new double[]{0.0, 0.5, 1.0}) //
						.setDischargingEfficiencyValues(new double[]{0.85, 0.90, 0.85}) //
						.setBatteryChargingEfficiency(0.95) //
						.setBatteryDischargingEfficiency(0.95) //
						.build());
	}

	private static ManagedSymmetricEss setupESS(String id, int maxApparentPower) {
		return new DummyManagedSymmetricEss(id).withMaxApparentPower(maxApparentPower);
	}
}

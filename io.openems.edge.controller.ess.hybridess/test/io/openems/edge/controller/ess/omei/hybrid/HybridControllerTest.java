package io.openems.edge.controller.ess.omei.hybrid;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.TimeLeapClock;
import io.openems.edge.controller.ess.hybridess.prediction.PredictionCSV;
import io.openems.edge.controller.ess.hybridess.controller.HybridControllerImpl;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid.ManagedSymmetricEssHybrid;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.test.DummyPower;
import io.openems.edge.meter.api.SymmetricMeter;
import io.openems.edge.meter.test.DummySymmetricMeter;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.StringJoiner;

import static org.junit.Assert.fail;

public class HybridControllerTest {

	@Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
	private static final String FOLDER = "CSVUtils";
	private Path energyPrediction;
	private Path powerPrediction;
	private static File tempDir;

	private TimeLeapClock clock;

	private static final String CTRL_ID = "ctrl0";
	private static final String MAIN_ID = "ess0";
	private static final String SUPPORT_ID ="ess1";
	
	private static final int MAIN_MAX_APPARENT_POWER = 100_000;
	private static final int SUPPORT_MAX_APPARENT_POWER = 276_000;

	private static final ChannelAddress MAIN_SOC = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.SOC.id());
	private static final ChannelAddress SUPPORT_SOC = new ChannelAddress(SUPPORT_ID, SymmetricEss.ChannelId.SOC.id());
	private static final ChannelAddress MAIN_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(MAIN_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	private static final ChannelAddress SUPPORT_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(SUPPORT_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	private static final ChannelAddress MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());

	private static final ChannelAddress MAIN_MAX_APPARENT_POWER_CHANNEL = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.MAX_APPARENT_POWER.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());

	private static final ChannelAddress MAIN_CAPACITY = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.CAPACITY.id());
	private static final ChannelAddress SUPPORT_CAPACITY = new ChannelAddress(SUPPORT_ID, SymmetricEss.ChannelId.CAPACITY.id());
	private static final ChannelAddress MAIN_ACTIVE_POWER = new ChannelAddress(MAIN_ID,
			SymmetricEss.ChannelId.ACTIVE_POWER.id());
	private static final ChannelAddress SUPPORT_ACTIVE_POWER = new ChannelAddress(SUPPORT_ID,
			SymmetricEss.ChannelId.ACTIVE_POWER.id());
	
	private static final String METER_ID = "meter0";
	private static final ChannelAddress METER_ACTIVE_POWER = new ChannelAddress(METER_ID, SymmetricMeter.ChannelId.ACTIVE_POWER.id());

	private static final String SUM_ID = "_sum";

	private static final ChannelAddress PRODUCTION_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.PRODUCTION_ACTIVE_POWER.id());
	private static final ChannelAddress CONSUMPTION_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.CONSUMPTION_ACTIVE_POWER.id());

	private static final ChannelAddress GRID_ACTIVE_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.GRID_ACTIVE_POWER.id());
	private static final int MAX_GRID_POWER = 200_000; // W
	private static final int DEFAULT_MIN_ENERGY = 100_000; // Wh
	
	@Test
	public void chargeSplit() throws Exception {

		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // chargeSplit#1
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5) // Both start in red area 0,05 * 400_000Wh = 20_000 <= 50_000
						.input(SUPPORT_SOC, 5)  // 0,05 * 276_000 = 13800
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -20_000) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -20_000) // power should be limited by filterPower
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -180_000)
						)
				.next(new TestCase() // chargeSplit#2
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5) // Both start in red area 0,05 * 400_000Wh = 20_000 <= 50_000
						.input(SUPPORT_SOC, 5)  // 0,05 * 276_000 = 13800
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(-0.5*MAX_GRID_POWER)) // power should be split equally.
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(-0.5*MAX_GRID_POWER)))
				.next(new TestCase() // chargeSplit#3
						.input(METER_ACTIVE_POWER,0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(PRODUCTION_POWER, MAX_GRID_POWER)
						.input(MAIN_SOC, 85) // MAIN green: 0.3 of chargePower
						.input(SUPPORT_SOC, 35) // SUPPORT orange: 0.7 of chargePower
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(-0.2*MAX_GRID_POWER))
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(-0.8*MAX_GRID_POWER)))
				.next(new TestCase() // chargeSplit#4
						.input(METER_ACTIVE_POWER,0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(PRODUCTION_POWER,0)
						.input(MAIN_SOC, 80) // MAIN green
						.input(SUPPORT_SOC, 5) // SUPPORT red
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -200_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -200_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER));
	}

	@Test
	public void chargePowerPrediction() throws Exception {
		int predictedPower = 100_000;
		ControllerTest controllerTest = createControllerTest();
		addPrediction("2022-12-08T10:00","2022-12-08T12:00", predictedPower, powerPrediction);

		controllerTest.next(new TestCase() // chargePowerPrediction#1
						.timeleap(clock,1, ChronoUnit.HOURS) // Advance to time window with prediction.
				.input(METER_ACTIVE_POWER,0) // Set consumption to 0
				.input(MAIN_CAPACITY, 400_000)
				.input(SUPPORT_CAPACITY, 276_000)
				.input(MAIN_SOC, 75) // Green SoC area
				.input(SUPPORT_SOC, 70)  // Green SoC area
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.output(MAIN_SET_ACTIVE_POWER_EQUALS, -predictedPower / 2)
				.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -predictedPower / 2)
		);
	}

	@Test
	public void chargeEnergyPrediction() throws Exception {
		int predictedEnergy = 500_000;
		ControllerTest controllerTest = createControllerTest();
		addPrediction("2022-12-08T10:00","2022-12-08T12:00", predictedEnergy, energyPrediction);
		controllerTest.next(new TestCase() // chargeEnergyPrediction#1
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 75) // Green SoC area 300_000Wh
						.input(SUPPORT_SOC, 70)  // Green SoC area 193_200Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0)
				).next(new TestCase() // chargeEnergyPrediction#2 Below energy minimum set by prediction.
						.timeleap(clock,1, ChronoUnit.HOURS) // Advance to time window with prediction.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 75) // Green SoC area 300_000Wh
						.input(SUPPORT_SOC, 70)  // Green SoC area 193_200Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -1700) // Missing energy 6800Wh over 2h -> 3400W split 50:50
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -1700))
				.next(new TestCase() // chargeEnergyPrediction#3
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 100) // Green SoC area 400_000Wh
						.input(SUPPORT_SOC, 70)  // Green SoC area 193_200Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0))
				.next(new TestCase() // chargeEnergyPrediction#4 Above energy minimum set by prediction.
						.timeleap(clock,30, ChronoUnit.MINUTES)
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 75) // Green SoC area 280_000Wh
						.input(SUPPORT_SOC, 70)  // Green SoC area 193_200Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -2266) // Missing energy 6800Wh over 1.5h -> 4533W split 50:50
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -2267)) // Support gets remaining 1W lost by rounding
				.next(new TestCase() // chargeEnergyPrediction#5; Below energy minimum. 1min remaining
						.timeleap(clock,89, ChronoUnit.MINUTES) // Advance to 1min before prediction elapses.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 75) // Green SoC area 300_000Wh
						.input(SUPPORT_SOC, 70)  // Green SoC area 193_200Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER/2) // Missing energy 6800Wh over 1min -> 1608000 limited by maxGridPower
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER/2))
				.next(new TestCase() // chargeEnergyPrediction#6
						.timeleap(clock, 10, ChronoUnit.HOURS) // Advance to time after prediction, with min Energy not met.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 75) // Green SoC area 300_000Wh
						.input(SUPPORT_SOC, 70)  // Green SoC area 193_200Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0) // No prediction and no energy from production
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0)
				);
	}

	@Test
	public void chargeUsesProduction() throws Exception {
		int productionPower = 400_000;
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // chargeUsesProduction#1
				.input(METER_ACTIVE_POWER,0) // Set consumption to 0
				.input(PRODUCTION_POWER, productionPower)
				.input(MAIN_CAPACITY, 400_000)
				.input(SUPPORT_CAPACITY, 276_000)
				.input(MAIN_SOC, 75)
				.input(SUPPORT_SOC, 70)
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.output(MAIN_SET_ACTIVE_POWER_EQUALS, -productionPower / 2)
				.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -productionPower / 2));
	}

	@Test
	public void chargeMinEnergy() throws Exception {
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // ChargeMinEnergy#1 Below energy minimum.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 10) // 40_000Wh
						.input(SUPPORT_SOC, 10)  // 27_600Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2))
				.next(new TestCase() // Above energy minimum.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 25) // 100_000Wh
						.input(SUPPORT_SOC, 25)  // 69_000Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0));
	}

	@Test
	public void filterChargePower() throws Exception {
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // filterChargePower#1 Below energy minimum.
					.input(METER_ACTIVE_POWER,0) // Set consumption to 0
					.input(MAIN_CAPACITY, 400_000)
					.input(SUPPORT_CAPACITY, 276_000)
					.input(MAIN_SOC, 10) // 40_000Wh
					.input(SUPPORT_SOC, 10)  // 27_600Wh
					.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -10_000)
					.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
					.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -12_000)
					.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
					.output(MAIN_SET_ACTIVE_POWER_EQUALS, -10_000)
					.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -12_000))
				.next(new TestCase() // filterChargePower#2 Below energy minimum.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 10) // 40_000Wh
						.input(SUPPORT_SOC, 10)  // 27_600Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, -150_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -150_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -50_000));
	}

	@Test
	public void filterDischargePower() throws Exception {
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // filterDischargePower#1
						.input(CONSUMPTION_POWER, 200_000)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 25) // Orange
						.input(SUPPORT_SOC, 25)  // Orange
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 20_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 20_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 100_000))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 100_000)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 25) // Orange
						.input(SUPPORT_SOC, 25)  // Orange
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 80_000)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 10_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 200_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 80_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 20_000));
	}


	@Test
	public void dischargeSplit() throws Exception {
		int required_power  = 200_000;
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // dischargeSplit#1
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 35)
						.input(SUPPORT_SOC, 35)  // 0,05 * 276_000 = 13800
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 20_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 20_000) // power should be limited by filterPower
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 180_000))
				.next(new TestCase() // dischargeSplit#2
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5)
						.input(SUPPORT_SOC, 5)  // 0,05 * 276_000 = 13800
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0) // conserve Energy at ESS in red -> Consumption will be covered by grid
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0)
				)
				.next(new TestCase() // dischargeSplit#3
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 35)
						.input(SUPPORT_SOC, 35)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(required_power*0.7)) // power should be limited by filterPower
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(required_power*0.3))
				)
				.next(new TestCase() // dischargeSplit#4
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 35)
						.input(SUPPORT_SOC, 80)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(required_power*0.3))
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(required_power*0.7))
				);
	}

	@Test
	public void dischargeNetLoad() throws Exception {
		int required_power  = 60_000;
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() //dischargeNetload#1
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 75)
						.input(SUPPORT_SOC, 70)
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, required_power)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 2*required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 75)
						.input(SUPPORT_SOC, 70)
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(0.7*2*required_power))
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(0.3*2*required_power)))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 25)
						.input(SUPPORT_SOC, 70)
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(0.3*required_power))
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(0.7*required_power)));
	}

	@Test
		public void reassignRemainder() throws Exception {
			ControllerTest controllerTest = createControllerTest();
			controllerTest.next(new TestCase() // reassigneRemainder#1 main did not get max; reassign remainder
							.input(METER_ACTIVE_POWER,0)
							.input(MAIN_CAPACITY, 400_000)
							.input(SUPPORT_CAPACITY, 276_000)
							.input(MAIN_SOC, 10) // red
							.input(SUPPORT_SOC, 10) //red
							.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
							.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
							.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -30_000)
							.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
							.output(MAIN_SET_ACTIVE_POWER_EQUALS, -170_000)
							.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -30_000))
					.next(new TestCase()
							.input(METER_ACTIVE_POWER,0)
							.input(CONSUMPTION_POWER, 200_000)
							.input(MAIN_CAPACITY, 400_000)
							.input(SUPPORT_CAPACITY, 276_000)
							.input(MAIN_SOC, 80) // green
							.input(SUPPORT_SOC, 80) // green
							.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
							.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
							.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
							.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 40_000)
							.output(MAIN_SET_ACTIVE_POWER_EQUALS, 160_000)
							.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 40_000));
	}

	@Test
	public void ProductionGreaterConsumption() throws Exception {
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase()
				.input(METER_ACTIVE_POWER,0)
				.input(CONSUMPTION_POWER, 200_000)
				.input(PRODUCTION_POWER, 300_000)
				.input(MAIN_CAPACITY, 400_000)
				.input(SUPPORT_CAPACITY, 276_000)
				.input(MAIN_SOC, 35) // orange
				.input(SUPPORT_SOC, 35) // orange
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.output(MAIN_SET_ACTIVE_POWER_EQUALS, -70_000)
				.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -30_000));
	}

	@Test
	public void bothRedBehaviorDischarge() throws Exception {
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase()
						.input(METER_ACTIVE_POWER,0)
						.input(CONSUMPTION_POWER, 140_000)
						.input(PRODUCTION_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 15) // red
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -30_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -30_000))
				.next(new TestCase()
						.input(METER_ACTIVE_POWER,0)
						.input(CONSUMPTION_POWER, 140_000)
						.input(PRODUCTION_POWER, 40_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 15) // red
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -50_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -50_000))
				.next(new TestCase()
						.input(METER_ACTIVE_POWER,0)
						.input(CONSUMPTION_POWER, 210_000)
						.input(PRODUCTION_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 15) // red
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 5_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 5_000))
				.next(new TestCase()
						.input(METER_ACTIVE_POWER,0)
						.input(CONSUMPTION_POWER, 280_000)
						.input(PRODUCTION_POWER, 40_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 15) // red
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 20_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 20_000))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 500_000)
						.input(PRODUCTION_POWER, 100_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 15) // red
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 50_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 50_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 50_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 50_000));
						//.output(GRID_ACTIVE_POWER,300_000)); // Not testable due to dummySumImpl
	}

	@Test
	public void oneRedBehavior() throws Exception {
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase()
						.input(CONSUMPTION_POWER, 140_000)
						.input(PRODUCTION_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 85) // green
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -60_000))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 280_000)
						.input(PRODUCTION_POWER, 40_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 35) // orange
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 40_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 280_000)
						.input(PRODUCTION_POWER, 40_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 85) // green
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 100_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 200_000)
						.input(PRODUCTION_POWER, 40_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 85) // green
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -40_000))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 380_000)
						.input(PRODUCTION_POWER, 40_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 85) // green
						.input(SUPPORT_SOC, 15) // red
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 100_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 40_000))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 0)
						.input(PRODUCTION_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 85)
						.input(SUPPORT_SOC, 15)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -200_000))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 500_000)
						.input(PRODUCTION_POWER, 100_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 15)
						.input(SUPPORT_SOC, 85)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 100_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 100_000))
				.next(new TestCase() // Test remainder -> There should be remainder
						.input(CONSUMPTION_POWER, 100_000)
						.input(PRODUCTION_POWER, 400_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 15)
						.input(SUPPORT_SOC, 85)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -100_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -250_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -100_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -250_000))
				.next(new TestCase() // Test remainder -> There should be no remainder
						.input(CONSUMPTION_POWER, 200_000)
						.input(PRODUCTION_POWER, 100_000)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 15)
						.input(SUPPORT_SOC, 85)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -50_000)
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 100_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -250_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -50_000)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0));
	}

	@Test
	public void redChargeInterval() throws Exception {
		ControllerTest controllerTest = new ControllerTest(new HybridControllerImpl()) //
				.addReference("componentManager", new DummyComponentManager(clock))
				.addReference("sum", new DummySum())
				.addComponent(setupESS(MAIN_ID, MAIN_MAX_APPARENT_POWER, new int[] {20,70}, new int[]{25,70}))
				.addComponent(setupESS(SUPPORT_ID, SUPPORT_MAX_APPARENT_POWER, new int[] {20, 50}, new int[]{25, 50}))//
				.addComponent(new DummySymmetricMeter(METER_ID)) //
				.activate(MyConfig.create()
						.setId(CTRL_ID)
						.setMainId(MAIN_ID)
						.setSupportId(SUPPORT_ID)//
						.setMeterId(METER_ID)
						.setEnergyPrediction(energyPrediction.toString())
						.setPowerPrediction(powerPrediction.toString())
						.setMaxGridPower(MAX_GRID_POWER)
						.setDefaultMinimumEnergy(DEFAULT_MIN_ENERGY)
						.build());

	}

	private ControllerTest createControllerTest() throws Exception {
		return createControllerTest(DEFAULT_MIN_ENERGY);
	}
	private ControllerTest createControllerTest(int defaultMinimumGridPower) throws Exception {
		LocalDateTime begin = LocalDateTime.parse("2022-12-08T09:00", DateTimeFormatter.ISO_DATE_TIME);
		clock = new TimeLeapClock(Instant.ofEpochSecond(begin.toEpochSecond(ZoneOffset.UTC)), ZoneOffset.UTC);

		energyPrediction = createPredictionFile("energyPrediction.csv");
		powerPrediction = createPredictionFile("powerPrediction.csv");

		return new ControllerTest(new HybridControllerImpl()) //
				.addReference("componentManager", new DummyComponentManager(clock))
				.addReference("sum", new DummySum())
				.addComponent(setupESS(MAIN_ID, MAIN_MAX_APPARENT_POWER))
				.addComponent(setupESS(SUPPORT_ID, SUPPORT_MAX_APPARENT_POWER))//
				.addComponent(new DummySymmetricMeter(METER_ID)) //
				.activate(MyConfig.create()
						.setId(CTRL_ID)
						.setMainId(MAIN_ID)
						.setSupportId(SUPPORT_ID)//
						.setMeterId(METER_ID)
						.setEnergyPrediction(energyPrediction.toString())
						.setPowerPrediction(powerPrediction.toString())
						.setMaxGridPower(MAX_GRID_POWER)
						.setDefaultMinimumEnergy(defaultMinimumGridPower)
						.build());
	}

	private static ManagedSymmetricEssHybrid setupESS(String id, int maxApparentPower) {
		return new DummyHybridEss(id, new DummyPower(maxApparentPower));
	}

	private static ManagedSymmetricEssHybrid setupESS(String id, int maxApparentPower,
													  int[] lowerSocBorder, int[] upperSocBorder) {
		return new DummyHybridEss(id, new DummyPower(maxApparentPower), lowerSocBorder, upperSocBorder);
	}

	private void addPrediction(String start, String end, int value, Path filepath) throws IOException {
		if(!Files.exists(tempDir.toPath()) || !Files.exists(filepath)) {
			fail(String.format("Could not find %s", filepath.toString()));
		}

		StringJoiner row = new StringJoiner(PredictionCSV.SEPARATOR);
		row.add(start).add(end).add(String.valueOf(value));
		Files.writeString(filepath,String.format("%s%s",row.toString(), System.lineSeparator()), StandardOpenOption.APPEND);
	}

	private Path createPredictionFile(String filename) throws IOException {
		try {
			tempDir = tempFolder.newFolder(FOLDER);
		} catch (IOException e) {
			if(!Files.exists(tempDir.toPath())) {
				throw e;
			}
			// Else Folder already exists -> ignore exception.
		}

		File predictionCSV = tempFolder.newFile(filename);

		StringJoiner fieldNames = new StringJoiner(PredictionCSV.SEPARATOR);
		fieldNames.add("START").add("END").add("VALUE");
		Files.writeString(predictionCSV.toPath(), fieldNames + System.lineSeparator(),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		return predictionCSV.toPath();
	}

}

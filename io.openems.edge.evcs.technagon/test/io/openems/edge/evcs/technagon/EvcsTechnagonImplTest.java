package io.openems.edge.evcs.technagon;

import org.junit.Test;

import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyConfigurationAdmin;
import io.openems.edge.evcs.api.ChargingType;
import io.openems.edge.evcs.api.Evcs;
import io.openems.edge.evcs.api.ManagedEvcs;
import io.openems.edge.evcs.api.Phases;
import io.openems.edge.evcs.api.Status;
import io.openems.edge.evcs.technagon.enums.TechnagonChargingPoint;
import io.openems.edge.evcs.technagon.enums.TechnagonConnectorType;
import io.openems.edge.evcs.technagon.enums.TechnagonState;
import io.openems.edge.evcs.test.DummyEvcsPower;

/**
 * Unit‑tests for {@link EvcsTechnagonImpl}.
 */
public class EvcsTechnagonImplTest {

	@Test
	public void mappingsAndLimits() throws Exception {
		var test = this.activateNewCharger(6000, 32000);

		// Startup limits: 6 A → 4 140 W, 32 A → 22 080 W
		test.next(new TestCase().output(Evcs.ChannelId.MINIMUM_HARDWARE_POWER, 4140)
				.output(Evcs.ChannelId.MAXIMUM_HARDWARE_POWER, 22080)
				.output(Evcs.ChannelId.PHASES, Phases.THREE_PHASE));

		// Apply 13 800 W limit → 20 000 mA ( W / (U·3) with U = 230 V )
		test.next(new TestCase().input(ManagedEvcs.ChannelId.SET_CHARGE_POWER_LIMIT, 13800)
				.output(EvcsTechnagon.ChannelId.DEBUG_SET_CHARGING_CURRENT, 20000));

		test.next(new TestCase().input(EvcsTechnagon.ChannelId.MIN_CHARGING_CURRENT, 10000)
				.input(EvcsTechnagon.ChannelId.MAX_CHARGING_CURRENT, 16000)
				.output(Evcs.ChannelId.MINIMUM_HARDWARE_POWER, 6900)
				.output(Evcs.ChannelId.MAXIMUM_HARDWARE_POWER, 11040));

		statusMapping(test, TechnagonState.AVAILABLE, Status.NOT_READY_FOR_CHARGING);
		statusMapping(test, TechnagonState.PREPARING, Status.READY_FOR_CHARGING);
		statusMapping(test, TechnagonState.CHARGING, Status.CHARGING);
		statusMapping(test, TechnagonState.EV_SUSPENDED, Status.CHARGING);
		statusMapping(test, TechnagonState.EVSE_SUSPENDED, Status.CHARGING);
		statusMapping(test, TechnagonState.RESERVED, Status.NOT_READY_FOR_CHARGING);
		statusMapping(test, TechnagonState.FINISHING, Status.READY_FOR_CHARGING);
		statusMapping(test, TechnagonState.FAULTED, Status.ERROR);
		statusMapping(test, TechnagonState.UNAVAILABLE, Status.CHARGING_REJECTED);
		statusMapping(test, TechnagonState.UNDEFINED, Status.UNDEFINED);

		connectorMapping(test, TechnagonConnectorType.CCS, ChargingType.CCS);
		connectorMapping(test, TechnagonConnectorType.TYPE_2, ChargingType.AC);
		connectorMapping(test, TechnagonConnectorType.TYPE_F, ChargingType.AC);
		connectorMapping(test, TechnagonConnectorType.UNDEFINED, ChargingType.UNDEFINED);
	}

	@Test
	public void configLimitsOverrideHardware() throws Exception {
		/* config: 10 A … 16 A | hardware: 6 A … 32 A → effective stays 10 A … 16 A */
		var test = this.activateNewCharger(10000, 16000);

		test.next(new TestCase().output(Evcs.ChannelId.MINIMUM_HARDWARE_POWER, 6900)
				.output(Evcs.ChannelId.MAXIMUM_HARDWARE_POWER, 11040));

		test.next(new TestCase().input(EvcsTechnagon.ChannelId.MIN_CHARGING_CURRENT, 6000)
				.input(EvcsTechnagon.ChannelId.MAX_CHARGING_CURRENT, 32000)
				.output(Evcs.ChannelId.MINIMUM_HARDWARE_POWER, 6900)
				.output(Evcs.ChannelId.MAXIMUM_HARDWARE_POWER, 11040));
	}

	private static void statusMapping(ComponentTest test, TechnagonState raw, Status expected) throws Exception {
		test.next(
				new TestCase().input(EvcsTechnagon.ChannelId.RAW_STATUS, raw).output(Evcs.ChannelId.STATUS, expected));
	}

	private static void connectorMapping(ComponentTest test, TechnagonConnectorType ct, ChargingType expected)
			throws Exception {
		test.next(new TestCase().input(EvcsTechnagon.ChannelId.ACTIVE_CONNECTOR, ct)
				.output(Evcs.ChannelId.CHARGING_TYPE, expected));
	}

	private ComponentTest activateNewCharger(int minHwCurrent, int maxHwCurrent) throws Exception {
		return new ComponentTest(new EvcsTechnagonImpl()).addReference("evcsPower", new DummyEvcsPower())
				.addReference("cm", new DummyConfigurationAdmin())
				.addReference("setModbus", new DummyModbusBridge("modbus0"))
				.activate(MyConfig.create().setId("evcs0").setDebugMode(false).setMinHwCurrent(minHwCurrent)
						.setMaxHwCurrent(maxHwCurrent).setModbusId("modbus0").setModbusUnitId(1)
						.setChargingPoint(TechnagonChargingPoint.CHARGING_POINT_1).setReadOnly(false).build());
	}
}

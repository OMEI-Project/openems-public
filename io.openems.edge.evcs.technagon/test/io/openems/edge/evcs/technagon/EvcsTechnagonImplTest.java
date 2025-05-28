package io.openems.edge.evcs.technagon;

import org.junit.Test;

import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyConfigurationAdmin;

public class EvcsTechnagonImplTest {

	@Test
	public void test() throws Exception {
		new ComponentTest(new EvcsTechnagonImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setModbus", new DummyModbusBridge("modbus0")) //
				.activate(MyConfig.create() //
						.setId("evcs0") //
						.setModbusId("modbus0") //
						.setChargingPoint(TechnagonChargingPoint.CHARGING_POINT_1) //
						.build()) //
				.next(new TestCase()) //
				.deactivate();
	}

}

package io.openems.edge.evcs.technagon;

import static io.openems.common.types.OpenemsType.INTEGER;

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
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.StringWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedQuadruplewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.evcs.api.ChargingType;
import io.openems.edge.evcs.api.Evcs;
import io.openems.edge.evcs.api.PhaseRotation;
import io.openems.edge.evcs.api.Status;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Evcs.TechnagonCharger", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class EvcsTechnagonImpl extends AbstractOpenemsModbusComponent
		implements EvcsTechnagon, Evcs, ElectricityMeter, ModbusComponent, OpenemsComponent {

	/* Global station‑wide registers (absolute addresses) */
	private static final int ABS_VENDOR = 0;
	private static final int ABS_DEVICE_TYPE = 5;
	private static final int ABS_LAYOUT_VERSION_MAJOR = 6;
	private static final int ABS_LAYOUT_VERSION_MINOR = 7;
	private static final int ABS_STATION_SERIAL = 8;
	private static final int ABS_STATION_MAX_CURRENT = 11;
	private static final int ABS_NUM_EVSES = 12;

	/* Modbus register offsets (relative to charging‑point base address) */
	private static final int REL_EVSE_SERIAL_NUMBER = 0;
	private static final int REL_ACTIVE_CONNECTOR = 2;
	private static final int REL_RAW_STATUS = 3;
	private static final int REL_EVSE_STATUS_LAST_UPDATED = 4;
	private static final int REL_MIN_CURRENT = 8;
	private static final int REL_MAX_CURRENT = 9;
	private static final int REL_CURRENT_OFFERED = 10;
	private static final int REL_VOLTAGE_L1 = 11;
	private static final int REL_VOLTAGE_L2 = 12;
	private static final int REL_VOLTAGE_L3 = 13;
	private static final int REL_POWER_FACTOR_L1 = 13;
	private static final int REL_POWER_FACTOR_L2 = 13;
	private static final int REL_POWER_FACTOR_L3 = 13;
	private static final int REL_CURRENT_L1 = 17;
	private static final int REL_CURRENT_L2 = 18;
	private static final int REL_CURRENT_L3 = 19;
	private static final int REL_POWER_L1 = 20;
	private static final int REL_POWER_L2 = 21;
	private static final int REL_POWER_L3 = 22;
	private static final int REL_POWER = 23;
	private static final int REL_ENERGY = 24;
	private static final int REL_SET_CURRENT_BP = 256; // write registers not yet implemented
	private static final int REL_SET_CURRENT_MA = 257; // write registers not yet implemented
	private static final int REL_FALL_BACK_CURRENT = 258;
	private static final int REL_FALL_BACK_TIMEOUT = 259;

	private static final ElementToChannelConverter DEVICE_CONVERTER = new ElementToChannelConverter(deviceCode -> {
		deviceCode = TypeUtils.<Integer>getAsType(INTEGER, deviceCode);
		return deviceCode.equals(1) ? "TE-P5/TE-P7/TEP4/TEP4HAK/TEW3/TEW4/TEP8" : null;
	});

	private static final ElementToChannelConverter CURRENT_LIMIT_TO_POWER_LIMIT = ElementToChannelConverter
			.chain(ElementToChannelConverter.SCALE_FACTOR_MINUS_3, ElementToChannelConverter.MULTIPLY(DEFAULT_VOLTAGE));

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	private Config config = null;

	public EvcsTechnagonImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				Evcs.ChannelId.values(), //
				EvcsTechnagon.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.MANAGED_CONSUMPTION_METERED;
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var cp = this.config.charging_point();
		final var modbusProtocol = new ModbusProtocol(this, new FC3ReadRegistersTask(0, Priority.LOW,

				// EvcsTechnagon Channels
				m(EvcsTechnagon.ChannelId.VENDOR, new StringWordElement(ABS_VENDOR, 5)),
				m(EvcsTechnagon.ChannelId.DEVICE, new UnsignedWordElement(ABS_DEVICE_TYPE), DEVICE_CONVERTER),
				m(EvcsTechnagon.ChannelId.MODBUS_REGISTER_LAYOUT_MAJOR_VERSION,
						new UnsignedWordElement(ABS_LAYOUT_VERSION_MAJOR)),
				m(EvcsTechnagon.ChannelId.MODBUS_REGISTER_LAYOUT_MINOR_VERSION,
						new UnsignedWordElement(ABS_LAYOUT_VERSION_MINOR)),
				m(EvcsTechnagon.ChannelId.STATION_SERIAL_NUMBER, new UnsignedDoublewordElement(ABS_STATION_SERIAL)),
				m(EvcsTechnagon.ChannelId.STATION_MAX_CURRENT, new UnsignedWordElement(ABS_STATION_MAX_CURRENT),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_3),
				m(EvcsTechnagon.ChannelId.NUM_EVSES, new UnsignedWordElement(ABS_NUM_EVSES)),
				m(EvcsTechnagon.ChannelId.EVSE_SERIAL_NUMBER,
						new UnsignedDoublewordElement(cp.applyModbusAddressOffset(REL_EVSE_SERIAL_NUMBER))),
				m(EvcsTechnagon.ChannelId.ACTIVE_CONNECTOR,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_ACTIVE_CONNECTOR))),
				m(EvcsTechnagon.ChannelId.RAW_STATUS,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_RAW_STATUS))),
				m(EvcsTechnagon.ChannelId.EVSE_STATUS_LAST_UPDATED,
						new UnsignedQuadruplewordElement(cp.applyModbusAddressOffset(REL_EVSE_STATUS_LAST_UPDATED))),
				m(EvcsTechnagon.ChannelId.MIN_CURRENT,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_MIN_CURRENT)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_3),
				m(EvcsTechnagon.ChannelId.MAX_CURRENT,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_MAX_CURRENT)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_3),
				m(EvcsTechnagon.ChannelId.CURRENT_OFFERED,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_CURRENT_OFFERED)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_3),
				m(EvcsTechnagon.ChannelId.POWER_FACTOR_L1,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_FACTOR_L1)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_2),
				m(EvcsTechnagon.ChannelId.POWER_FACTOR_L2,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_FACTOR_L2)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_2),
				m(EvcsTechnagon.ChannelId.POWER_FACTOR_L3,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_FACTOR_L3)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_2),
				m(EvcsTechnagon.ChannelId.FALL_BACK_CURRENT,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_FALL_BACK_CURRENT)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_2),
				m(EvcsTechnagon.ChannelId.FALL_BACK_TIMEOUT,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_FALL_BACK_TIMEOUT)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_2),

				// Evcs channels
				m(Evcs.ChannelId.FIXED_MINIMUM_HARDWARE_POWER,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_MIN_CURRENT)),
						CURRENT_LIMIT_TO_POWER_LIMIT),
				m(Evcs.ChannelId.FIXED_MAXIMUM_HARDWARE_POWER,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_MAX_CURRENT)),
						CURRENT_LIMIT_TO_POWER_LIMIT),
				m(Evcs.ChannelId.ENERGY_SESSION, new UnsignedWordElement(cp.applyModbusAddressOffset(REL_ENERGY))),

				// Electricity meter channels
				m(ElectricityMeter.ChannelId.ACTIVE_POWER_L1,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_L1))),
				m(ElectricityMeter.ChannelId.ACTIVE_POWER_L2,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_L2))),
				m(ElectricityMeter.ChannelId.ACTIVE_POWER_L3,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_L3))),
				m(ElectricityMeter.ChannelId.ACTIVE_POWER,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER))),
				m(ElectricityMeter.ChannelId.CURRENT_L1,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_CURRENT_L1))),
				m(ElectricityMeter.ChannelId.CURRENT_L2,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_CURRENT_L2))),
				m(ElectricityMeter.ChannelId.CURRENT_L3,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_CURRENT_L3))),
				m(ElectricityMeter.ChannelId.VOLTAGE_L1,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_VOLTAGE_L1)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_1),
				m(ElectricityMeter.ChannelId.VOLTAGE_L2,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_VOLTAGE_L2)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_1),
				m(ElectricityMeter.ChannelId.VOLTAGE_L3,
						new UnsignedWordElement(cp.applyModbusAddressOffset(REL_VOLTAGE_L3)),
						ElementToChannelConverter.SCALE_FACTOR_MINUS_1)

		));

		this.addStatusListener();
		this.addActiveConnectorListener();
		Evcs.calculateUsedPhasesFromCurrent(this);
		Evcs.addCalculatePowerLimitListeners(this);
		ElectricityMeter.calculateAverageVoltageFromPhases(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);

		return modbusProtocol;
	}

	private void addStatusListener() {
		this.channel(EvcsTechnagon.ChannelId.RAW_STATUS).onSetNextValue(s -> {
			TechnagonState rawState = s.asEnum();
			Status status = switch (rawState) {
			case AVAILABLE -> Status.NOT_READY_FOR_CHARGING;
			case PREPARING -> Status.READY_FOR_CHARGING;
			case CHARGING -> Status.CHARGING;
			case EV_SUSPENDED, EVSE_SUSPENDED, RESERVED, FINISHING -> Status.CHARGING_REJECTED;
			case FAULTED, UNAVAILABLE -> Status.ERROR;
			case UNDEFINED -> Status.UNDEFINED;
			};
			this._setStatus(status);
		});
	}

	private void addActiveConnectorListener() {
		this.channel(EvcsTechnagon.ChannelId.ACTIVE_CONNECTOR).onSetNextValue(activeConnector -> {
			ChargingType type = switch (activeConnector.asEnum()) {
			case TechnagonConnectorType.TYPE_2, TechnagonConnectorType.TYPE_F -> ChargingType.AC;
			case TechnagonConnectorType.CCS -> ChargingType.CCS;
			default -> ChargingType.UNDEFINED;
			};
			this._setChargingType(type);
		});

	}

	@Override
	public String debugLog() {
		return this.getStatus().getName();
	}

	@Override
	public PhaseRotation getPhaseRotation() {
		return PhaseRotation.L1_L2_L3;
	}

}

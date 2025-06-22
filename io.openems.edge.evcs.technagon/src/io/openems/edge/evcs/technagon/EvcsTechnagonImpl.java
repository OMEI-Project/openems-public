package io.openems.edge.evcs.technagon;

import static io.openems.common.types.OpenemsType.INTEGER;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
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
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.evcs.api.ChargeStateHandler;
import io.openems.edge.evcs.api.ChargingType;
import io.openems.edge.evcs.api.Evcs;
import io.openems.edge.evcs.api.EvcsPower;
import io.openems.edge.evcs.api.ManagedEvcs;
import io.openems.edge.evcs.api.PhaseRotation;
import io.openems.edge.evcs.api.Phases;
import io.openems.edge.evcs.api.Status;
import io.openems.edge.evcs.api.WriteHandler;
import io.openems.edge.evcs.technagon.enums.TechnagonConnectorType;
import io.openems.edge.evcs.technagon.enums.TechnagonState;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Evcs.TechnagonCharger", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE, //
})
public class EvcsTechnagonImpl extends AbstractOpenemsModbusComponent
		implements EvcsTechnagon, ManagedEvcs, EventHandler, Evcs, ElectricityMeter, ModbusComponent, OpenemsComponent {

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
	private static final int REL_MIN_CHARHING_CURRENT = 8;
	private static final int REL_MAX_CHARGING_CURRENT = 9;
	private static final int REL_CURRENT_OFFERED = 10;
	private static final int REL_VOLTAGE_L1 = 11;
	private static final int REL_VOLTAGE_L2 = 12;
	private static final int REL_VOLTAGE_L3 = 13;
	private static final int REL_POWER_FACTOR_L1 = 14;
	private static final int REL_POWER_FACTOR_L2 = 15;
	private static final int REL_POWER_FACTOR_L3 = 16;
	private static final int REL_CURRENT_L1 = 17;
	private static final int REL_CURRENT_L2 = 18;
	private static final int REL_CURRENT_L3 = 19;
	private static final int REL_POWER_L1 = 20;
	private static final int REL_POWER_L2 = 21;
	private static final int REL_POWER_L3 = 22;
	private static final int REL_POWER = 23;
	private static final int REL_ENERGY = 24;
	private static final int REL_SET_CURRENT_MA = 257;
	private static final int REL_FALL_BACK_CURRENT = 258;
	private static final int REL_FALL_BACK_TIMEOUT = 259;

	private static final ElementToChannelConverter DEVICE_CONVERTER = new ElementToChannelConverter(deviceCode -> {
		if (deviceCode == null) {
			return null;
		}

		deviceCode = TypeUtils.<Integer>getAsType(INTEGER, deviceCode);
		return deviceCode.equals(0) ? "TE-P5/TE-P7/TEP4/TEP4HAK/TEW3/TEW4/TEP8" : null;
	});

	private final Logger log = LoggerFactory.getLogger(EvcsTechnagon.class);

	/**
	 * Handles charge states.
	 */
	private final ChargeStateHandler chargeStateHandler = new ChargeStateHandler(this);

	/**
	 * Processes the controller's writes to this evcs component.
	 */
	private final WriteHandler writeHandler = new WriteHandler(this);

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	@Reference
	private EvcsPower evcsPower;

	private Config config;

	public EvcsTechnagonImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				Evcs.ChannelId.values(), //
				ManagedEvcs.ChannelId.values(), //
				EvcsTechnagon.ChannelId.values(), //
				ElectricityMeter.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}

		this.onActivateOrModified();
	}

	@Modified
	private void modified(ComponentContext context, Config config) throws OpenemsNamedException {
		this.config = config;
		if (super.modified(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}

		this.onActivateOrModified();
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
	public String debugLog() {
		return "Limit:" + this.getSetChargePowerLimit().asString() + "|" + this.getStatus().getName();
	}

	@Override
	public PhaseRotation getPhaseRotation() {
		return PhaseRotation.L1_L2_L3;
	}

	@Override
	public boolean isReadOnly() {
		return this.config.readOnly();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var cp = this.config.charging_point();
		final var modbusProtocol = new ModbusProtocol(this,

				// EvcsTechnagon Channels
				new FC3ReadRegistersTask(ABS_VENDOR, Priority.LOW,
						m(EvcsTechnagon.ChannelId.VENDOR, new StringWordElement(ABS_VENDOR, 5))),
				new FC3ReadRegistersTask(ABS_DEVICE_TYPE, Priority.LOW,
						m(EvcsTechnagon.ChannelId.DEVICE, new UnsignedWordElement(ABS_DEVICE_TYPE), DEVICE_CONVERTER)),
				new FC3ReadRegistersTask(ABS_LAYOUT_VERSION_MAJOR, Priority.LOW,
						m(EvcsTechnagon.ChannelId.MODBUS_REGISTER_LAYOUT_MAJOR_VERSION,
								new UnsignedWordElement(ABS_LAYOUT_VERSION_MAJOR))),
				new FC3ReadRegistersTask(ABS_LAYOUT_VERSION_MINOR, Priority.LOW,
						m(EvcsTechnagon.ChannelId.MODBUS_REGISTER_LAYOUT_MINOR_VERSION,
								new UnsignedWordElement(ABS_LAYOUT_VERSION_MINOR))),
				new FC3ReadRegistersTask(ABS_STATION_SERIAL, Priority.LOW,
						m(EvcsTechnagon.ChannelId.STATION_SERIAL_NUMBER,
								new UnsignedDoublewordElement(ABS_STATION_SERIAL))),
				new FC3ReadRegistersTask(ABS_STATION_MAX_CURRENT, Priority.LOW,
						m(EvcsTechnagon.ChannelId.STATION_MAX_CURRENT, new UnsignedWordElement(ABS_STATION_MAX_CURRENT),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_3)),
				new FC3ReadRegistersTask(ABS_NUM_EVSES, Priority.LOW,
						m(EvcsTechnagon.ChannelId.NUM_EVSES, new UnsignedWordElement(ABS_NUM_EVSES))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_EVSE_SERIAL_NUMBER), Priority.LOW,
						m(EvcsTechnagon.ChannelId.EVSE_SERIAL_NUMBER,
								new UnsignedDoublewordElement(cp.applyModbusAddressOffset(REL_EVSE_SERIAL_NUMBER)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_ACTIVE_CONNECTOR), Priority.LOW,
						m(EvcsTechnagon.ChannelId.ACTIVE_CONNECTOR,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_ACTIVE_CONNECTOR)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_RAW_STATUS), Priority.LOW,
						m(EvcsTechnagon.ChannelId.RAW_STATUS,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_RAW_STATUS)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_EVSE_STATUS_LAST_UPDATED), Priority.LOW,
						m(EvcsTechnagon.ChannelId.EVSE_STATUS_LAST_UPDATED,
								new UnsignedQuadruplewordElement(
										cp.applyModbusAddressOffset(REL_EVSE_STATUS_LAST_UPDATED)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_MIN_CHARHING_CURRENT), Priority.LOW,
						m(EvcsTechnagon.ChannelId.MIN_CHARGING_CURRENT,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_MIN_CHARHING_CURRENT)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_MAX_CHARGING_CURRENT), Priority.LOW,
						m(EvcsTechnagon.ChannelId.MAX_CHARGING_CURRENT,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_MAX_CHARGING_CURRENT)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_CURRENT_OFFERED), Priority.LOW,
						m(EvcsTechnagon.ChannelId.CURRENT_OFFERED,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_CURRENT_OFFERED)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_POWER_FACTOR_L1), Priority.LOW,
						m(EvcsTechnagon.ChannelId.POWER_FACTOR_L1,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_FACTOR_L1)),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_POWER_FACTOR_L2), Priority.LOW,
						m(EvcsTechnagon.ChannelId.POWER_FACTOR_L2,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_FACTOR_L2)),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_POWER_FACTOR_L3), Priority.LOW,
						m(EvcsTechnagon.ChannelId.POWER_FACTOR_L3,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_FACTOR_L3)),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_FALL_BACK_CURRENT), Priority.LOW,
						m(EvcsTechnagon.ChannelId.FALL_BACK_CURRENT,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_FALL_BACK_CURRENT)),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_FALL_BACK_TIMEOUT), Priority.LOW,
						m(EvcsTechnagon.ChannelId.FALL_BACK_TIMEOUT,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_FALL_BACK_TIMEOUT)),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_2)),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_ENERGY), Priority.LOW,
						m(Evcs.ChannelId.ENERGY_SESSION,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_ENERGY)))),

				// Electricity meter channels
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_POWER_L1), Priority.LOW,
						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L1,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_L1)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_POWER_L2), Priority.LOW,
						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L2,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_L2)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_POWER_L3), Priority.LOW,
						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L3,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER_L3)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_POWER), Priority.LOW,
						m(ElectricityMeter.ChannelId.ACTIVE_POWER,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_POWER)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_CURRENT_L1), Priority.LOW,
						m(ElectricityMeter.ChannelId.CURRENT_L1,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_CURRENT_L1)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_CURRENT_L2), Priority.LOW,
						m(ElectricityMeter.ChannelId.CURRENT_L2,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_CURRENT_L2)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_CURRENT_L3), Priority.LOW,
						m(ElectricityMeter.ChannelId.CURRENT_L3,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_CURRENT_L3)))),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_VOLTAGE_L1), Priority.LOW,
						m(ElectricityMeter.ChannelId.VOLTAGE_L1,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_VOLTAGE_L1)),
								ElementToChannelConverter.SCALE_FACTOR_2)),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_VOLTAGE_L2), Priority.LOW,
						m(ElectricityMeter.ChannelId.VOLTAGE_L2,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_VOLTAGE_L2)),
								ElementToChannelConverter.SCALE_FACTOR_2)),
				new FC3ReadRegistersTask(cp.applyModbusAddressOffset(REL_VOLTAGE_L3), Priority.LOW,
						m(ElectricityMeter.ChannelId.VOLTAGE_L3,
								new UnsignedWordElement(cp.applyModbusAddressOffset(REL_VOLTAGE_L3)),
								ElementToChannelConverter.SCALE_FACTOR_2)));

		if (!this.isReadOnly()) {
			modbusProtocol.addTask(new FC6WriteRegisterTask(cp.applyModbusAddressOffset(REL_SET_CURRENT_MA),
					m(EvcsTechnagon.ChannelId.SET_CHARGING_CURRENT,
							new UnsignedWordElement(cp.applyModbusAddressOffset(REL_SET_CURRENT_MA)))));
		}

		this.addStatusListener();
		this.addActiveConnectorListener();
		this.addMinCurrentListener();
		this.addMaxCurrentListener();
		Evcs.calculateUsedPhasesFromCurrent(this);
		Evcs.addCalculatePowerLimitListeners(this);
		ElectricityMeter.calculateAverageVoltageFromPhases(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);

		return modbusProtocol;
	}

	@Override
	public EvcsPower getEvcsPower() {
		return this.evcsPower;
	}

	@Override
	public int getConfiguredMinimumHardwarePower() {
		return this.toWatts(this.config.minHwCurrent());
	}

	@Override
	public int getConfiguredMaximumHardwarePower() {
		return this.toWatts(this.config.maxHwCurrent());
	}

	@Override
	public boolean getConfiguredDebugMode() {
		return this.config.debugMode();
	}

	@Override
	public boolean applyChargePowerLimit(int power) throws OpenemsNamedException {
		if (this.isReadOnly()) {
			return false;
		} else {
			var phases = this.getPhasesAsInt();
			var currentMilliAmps = Math.round((power / ((float) phases * DEFAULT_VOLTAGE)) * 1000f);

			this.setSetChargingCurrent(currentMilliAmps);

			return true;
		}
	}

	@Override
	public boolean pauseChargeProcess() throws Exception {
		return this.applyChargePowerLimit(0);
	}

	@Override
	public boolean applyDisplayText(String text) throws OpenemsException {
		return false;
	}

	@Override
	public int getMinimumTimeTillChargingLimitTaken() {
		return 10; // car needs at least 5 seconds
	}

	@Override
	public ChargeStateHandler getChargeStateHandler() {
		return this.chargeStateHandler;
	}

	@Override
	public void logDebug(String message) {
		this.logDebug(this.log, message);

	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE //
			-> this.writeHandler.run();
		}
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

	private void addMinCurrentListener() {
		this.channel(EvcsTechnagon.ChannelId.MIN_CHARGING_CURRENT).onSetNextValue(minCurrent -> {
			var readMinCurrent = (Integer) minCurrent.get();
			this.updateFixedMinimumHardwarePower(readMinCurrent);
		});
	}

	private void addMaxCurrentListener() {
		this.channel(EvcsTechnagon.ChannelId.MAX_CHARGING_CURRENT).onSetNextValue(maxCurrent -> {
			var readMaxCurrent = (Integer) maxCurrent.get();
			this.updateFixedMaximumHardwarePower(readMaxCurrent);
		});
	}

	private int toWatts(int milliAmps) {
		return Math.round(milliAmps / 1000f) * DEFAULT_VOLTAGE * Phases.THREE_PHASE.getValue();
	}

	private void onActivateOrModified() {
		this._setPowerPrecision(0.23);

		var readMinCurrent = this.getMinChargingCurrent().get();
		var readMaxCurrent = this.getMaxChargingCurrent().get();
		this.updateFixedMinimumHardwarePower(readMinCurrent);
		this.updateFixedMaximumHardwarePower(readMaxCurrent);
	}

	private void updateFixedMinimumHardwarePower(Integer readMinCurrent) {
		var effectiveCurrent = readMinCurrent == null ? this.config.minHwCurrent()
				: Math.max(readMinCurrent, this.config.minHwCurrent());
		var power = this.toWatts(effectiveCurrent);

		this._setFixedMinimumHardwarePower(power);
	}

	private void updateFixedMaximumHardwarePower(Integer readMaxCurrent) {
		var effectiveCurrent = readMaxCurrent == null ? this.config.maxHwCurrent()
				: Math.min(readMaxCurrent, this.config.maxHwCurrent());
		var power = this.toWatts(effectiveCurrent);

		this._setFixedMaximumHardwarePower(power);
	}
}

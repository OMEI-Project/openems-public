package io.openems.edge.simulator.pythonBridge;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.test.TimeLeapClock;
import io.openems.edge.simulator.ess.symmetric.hybrid.EssSymmetricHybrid;

import org.osgi.service.event.Event;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import io.openems.edge.controller.api.Controller;


@SuppressWarnings("restriction")
public class SimulatedCycleWorker {

    private SimulatedSum sum;
	private SimulatedPower power;
    private EssSymmetricHybrid main;
    private EssSymmetricHybrid support;
    private Controller controller;
    private TimeLeapClock clock;

    private Event afterProcessImageEvent;
    private Event beforeWriteEvent;
    private Event afterWriteEvent;
    
    private int currentConsumption = 0;
    private int currentProduction = 0;
    private Instant currentDateTime;
    
    private int mainActivePower;
    private int supportActivePower;
    private int hessActivePower;
    
    private int mainSoc;
    private int supportSoc;
    private int hessSoc;
    
    private int gridActivePower;
    
    //  private int autarky;
    // private int selfConsumption;
    
    public SimulatedCycleWorker(SimulatedSum sum, SimulatedPower power, EssSymmetricHybrid main,
    							EssSymmetricHybrid support, Controller controller,
                                ComponentManager componentManager, TimeLeapClock clock) {
        this.sum = sum;
        this.power = power;
        this.main = main;
        this.support = support;
        this.controller = controller;
        this.clock = clock;
        
        afterProcessImageEvent = new Event(EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE, new HashMap<>());
        beforeWriteEvent = new Event(EdgeEventConstants.TOPIC_CYCLE_BEFORE_WRITE, new HashMap<>());
        afterWriteEvent = new Event(EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE, new HashMap<>());
    }
    
    public void executeCycle(int production, int consumption) {
		main.handleEvent(afterProcessImageEvent);
		support.handleEvent(afterProcessImageEvent);

		// Write next values to channels
		ChannelUpdater.updateChannels();

		mainSoc = main.getSocChannel().getNextValue().orElse(-1);
		supportSoc = support.getSocChannel().getNextValue().orElse(-1);
		hessSoc = (mainSoc + supportSoc) / 2;

    	sum.setConsumption(consumption);
    	sum.setProduction(production);
    	try {
			controller.run();

			// Just write power value for ESS.
			power.handleEvent(afterWriteEvent);
		} catch (OpenemsNamedException e) {
			e.printStackTrace();
		}
		mainActivePower = main.getActivePowerChannel().getNextValue().orElse(0);
		supportActivePower = support.getActivePowerChannel().getNextValue().orElse(0);
		hessActivePower = mainActivePower + supportActivePower;

		gridActivePower = -(hessActivePower);
		gridActivePower+=consumption;
		gridActivePower-=production;

    	currentConsumption = consumption;
    	currentProduction = production;

		currentDateTime = Instant.now(clock);
		clock.leap(1, ChronoUnit.SECONDS);

    }
    
    /*
    public int autarky(int hessActivePower, int gridActivePower, int consumption) {
    	int autarky = 0;
    	if(hessActivePower > 0) {
    		hessActivePower = 0;
    	} else {
    		hessActivePower = hessActivePower*-1;
    	}
    	
    	if(gridActivePower < 0) {
    		gridActivePower = 0;
    	}
    	
    	if (consumption <= 0 && hessActivePower <= 0) {
    		autarky = 100;
    	} else {
    		autarky = double(1 - (gridActivePower / double(consumption + hessActivePower))) * 100
    		autarky = Math.max(0, Math.min(100, autarky))
    	}
    	
    	return autarky;
    }
    */
    
    public int getCurrentConsumption() {
		return currentConsumption;
	}

	public int getCurrentProduction() {
		return currentProduction;
	}

	public long getCurrentDateTime() {
		return currentDateTime.getEpochSecond();
	}

	public int getMainActivePower() {
		return mainActivePower;
	}

	public int getSupportActivePower() {
		return supportActivePower;
	}

	public int getHessActivePower() {
		return hessActivePower;
	}

	public int getMainSoc() {
		return mainSoc;
	}

	public int getSupportSoc() {
		return supportSoc;
	}

	public int getHessSoc() {
		return hessSoc;
	}

	public int getGridActivePower() {
		return gridActivePower;
	}

	/*
	public int getAutarky() {
		return autarky;
	}

	public int getSelfConsumption() {
		return selfConsumption;
	}
	*/
}

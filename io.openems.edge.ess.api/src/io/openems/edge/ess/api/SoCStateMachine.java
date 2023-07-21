package io.openems.edge.ess.api;

import static io.openems.edge.ess.api.SocState.*;


public class SoCStateMachine {
    private SocState currentState = ORANGE;
    private final int[] lowerBorder;
    private final int[] upperBorder;

    public SoCStateMachine(int[] lowerBorder, int[] upperBorder) {
        this.lowerBorder = lowerBorder;
        this.upperBorder = upperBorder;
    }

    public void calculateSoCState(int soc) {
        SocState nextState = currentState;
        switch(currentState) {
            case GREEN:
                if (soc <= lowerBorder[0]) {
                    nextState = RED;
                } else if (soc <= lowerBorder[1]) {
                    nextState = ORANGE;
                }
                break;
            case ORANGE:
                if (soc <= lowerBorder[0]) {
                    nextState = RED;
                } else if (soc > upperBorder[1]) {
                    nextState = GREEN;
                }
                break;
            case RED:
                if (soc > upperBorder[1]) {
                    nextState = GREEN;
                }else if(soc > upperBorder[0]) {
                    nextState = ORANGE;
                }
                break;
            default:
                throw new IllegalStateException(String.format("Encountered unknown SocState: %s.", currentState));
        }
        this.currentState = nextState;
    }

    public SocState getSoCState(){
        return this.currentState;
    }
}

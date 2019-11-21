package de.metro.robocode;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;

public class Offset extends AdvancedRobot {
    private StateMachine movementControl = new StateMachine();

    public void run() {

        movementControl.changeState(new BrawlState(this, movementControl));

        setColors(java.awt.Color.BLACK, java.awt.Color.RED, java.awt.Color.BLACK);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        do {
            // basic mini-radar code
            movementControl.executeStateUpdate();
            execute();
        } while (true);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        movementControl.excecuteOnScannedRobot(e);
    }
}

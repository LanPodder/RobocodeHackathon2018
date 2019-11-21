package de.metro.robocode;

import robocode.ScannedRobotEvent;

public interface IState {

	public void enter();

	public void doState();

	public void exit();

	public void onScannedRobot(ScannedRobotEvent e);
}

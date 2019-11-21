package de.metro.robocode;

import robocode.ScannedRobotEvent;

public class StateMachine {

	public IState currentState;
	private IState previousState;

	public void changeState(IState newState) {
		if (currentState != null) {
			this.currentState.exit();
		}
		this.previousState = this.currentState;

		this.currentState = newState;
		currentState.enter();
	}

	public void executeStateUpdate() {
		IState runningState = this.currentState;
		if (runningState != null) {
			this.currentState.doState();
		}
	}

	public void excecuteOnScannedRobot(ScannedRobotEvent e) {
		IState runningState = this.currentState;
		if (runningState != null) {
			this.currentState.onScannedRobot(e);
		}
	}

	public void switchToPreviousState() {
		this.currentState.exit();
		this.currentState = this.previousState;
		this.currentState.enter();
	}
}

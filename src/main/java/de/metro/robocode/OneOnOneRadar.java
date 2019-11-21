package de.metro.robocode;

import robocode.AdvancedRobot;
import robocode.Robot;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class OneOnOneRadar implements IState {

	AdvancedRobot bot;

	public OneOnOneRadar(AdvancedRobot bot) {
		this.bot = bot;
	}

	@Override
	public void enter() {

	}

	@Override
	public void exit() {

	}

	public void onScannedRobot(ScannedRobotEvent e) {
		double bearing = e.getBearingRadians() + bot.getHeadingRadians();
		double radarTurn = Utils.normalRelativeAngle(bearing - bot.getRadarHeadingRadians());

		// Distance we want to scan from middle of enemy to either side
		// The 36.0 is how many units from the center of the enemy robot it scans.
		double extraTurn = Math.min(Math.atan(36.0 / e.getDistance()), Rules.RADAR_TURN_RATE_RADIANS);

		// Adjust the radar turn so it goes that much further in the direction it is
		// going to turn
		// Basically if we were going to turn it left, turn it even more left, if right,
		// turn more right.
		// This allows us to overshoot our enemy so that we get a good sweep that will
		// not slip.
		// give the radar some bonus turn so it doesnt miss the enemy
		if (radarTurn < 0)
			radarTurn -= extraTurn;
		else
			radarTurn += extraTurn;

		// Turn the radar
		bot.setTurnRadarRightRadians(radarTurn);
	}

	@Override
	public void doState() {
		if (bot.getRadarTurnRemaining() == 0)
			bot.setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
	}

}

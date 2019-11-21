package de.metro.robocode;

import robocode.*;
import java.util.*;
import java.awt.geom.*;
import java.awt.*;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Enumeration;
import java.util.Hashtable;

import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.RobotDeathEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class BrawlState implements IState {

	public static Rectangle2D.Double _fieldRect = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 564);
	static Point2D.Double myLocation, last;
	static EnemyInfo currentTarget;
	static Hashtable enemies;

	StateMachine machine;
	AdvancedRobot bot;

	public BrawlState(AdvancedRobot bot, StateMachine machine) {
		this.bot = bot;
		this.machine = machine;
	}

	ArrayList gravpoints;
	double bfWidth, bfHeight;

	@Override
	public void enter() {
		bfWidth = bot.getBattleFieldWidth();
		bfHeight = bot.getBattleFieldHeight();
		bot.setTurnRadarRight(Double.POSITIVE_INFINITY);
		bot.setColors(Color.gray, null, Color.red);
		enemies = new Hashtable();
		gravpoints = new ArrayList();
	}

	Point2D.Double next = currentTarget = null;

	@Override
	public void doState() {
		myLocation = new Point2D.Double(bot.getX(), bot.getY());
		if (currentTarget != null) {
			if (next == null)
				next = last = myLocation;
			boolean changed = false;
			double angle = 0, distance;
			do {
				Point2D.Double p;
				if (new Rectangle2D.Double(30, 30, bot.getBattleFieldWidth() - 60, bot.getBattleFieldHeight() - 60)
						.contains(p = AdditionalUtils.project(myLocation, angle,
								Math.min((distance = myLocation.distance(currentTarget)) / 2, 300)))
						&& findRisk(p) < findRisk(next)) {
					changed = true;
					next = p;
				}
				angle += .1;
			} while (angle < Math.PI * 2);
			if (changed)
				last = myLocation;
			if (bot.getEnergy() / distance > .005)
				bot.setFire(60 * Math.min(currentTarget.energy, bot.getEnergy()) / distance);
			bot.setTurnGunRightRadians(robocode.util.Utils
					.normalRelativeAngle(AdditionalUtils.absoluteBearing(currentTarget, myLocation) - bot.getGunHeadingRadians()));
			double turn;
			if (Math.cos(turn = AdditionalUtils.absoluteBearing(next, myLocation) - bot.getHeadingRadians()) < 0) {
				turn += Math.PI;
				distance = -distance;
			}
			bot.setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(turn));
			bot.setAhead((Math.abs(bot.getTurnRemainingRadians()) > 1) ? 0 : distance);
		}
	}

	/**
	 * essentially your rotation is constantly influenced by other "GravPoint"s
	 * the closer/the more important a GravPoint is, the more you turn away from it
	 */
	public void antiGravMove() {
		double xforce = 0;
		double yforce = 0;
		double force;
		double ang;
		GravPoint p;

		// quick maths
		for (int i = 0; i < gravpoints.size(); i++) {
			p = (GravPoint) gravpoints.get(i);
			force = p.power / Math.pow(getRange(bot.getX(), bot.getY(), p.x, p.y), 2);
			ang = Utils.normalRelativeAngle(Math.PI / 2 - Math.atan2(bot.getY() - p.y, bot.getX() - p.x));
			xforce += Math.sin(ang) * force;
			yforce += Math.cos(ang) * force;
		}

		// repulsive force from walls
		xforce += 5000 / Math.pow(getRange(bot.getX(), bot.getY(), bot.getBattleFieldWidth(), bot.getY()), 3);
		xforce -= 5000 / Math.pow(getRange(bot.getX(), bot.getY(), 0, bot.getY()), 3);
		yforce += 5000 / Math.pow(getRange(bot.getX(), bot.getY(), bot.getX(), bot.getBattleFieldHeight()), 3);
		yforce -= 5000 / Math.pow(getRange(bot.getX(), bot.getY(), bot.getX(), 0), 3);

		goTo(bot.getX() - xforce, bot.getY() - yforce);
	}

	public void goTo(double x, double y) {
		double dist = 20;
		double angle = Math.toDegrees(AdditionalUtils.absoluteBearing(new Point2D.Double(bot.getX(), bot.getY()), new Point2D.Double(x, y)));
		double r = turnTo(angle);
		bot.setAhead(dist * r);
	}

	int turnTo(double angle) {
		double ang;
		int dir;
		ang = Utils.normalRelativeAngleDegrees(bot.getHeading() - angle);
		if (ang > 90) {
			ang -= 180;
			dir = -1;
		} else if (ang < -90) {
			ang += 180;
			dir = -1;
		} else {
			dir = 1;
		}
		bot.setTurnLeft(ang);
		return dir;
	}

	// simple linear algebra
	double getRange(double x1, double y1, double x2, double y2) {
		double x = x2 - x1;
		double y = y2 - y1;
		double range = Math.sqrt(x * x + y * y);
		return range;
	}

	private double findRisk(Point2D.Double point) {
		double risk = 4 / last.distanceSq(point) + .1 / myLocation.distanceSq(point);
		Enumeration enum1 = enemies.elements();
		do {
			EnemyInfo e;
			double thisrisk = Math.max(bot.getEnergy(), (e = (EnemyInfo) enum1.nextElement()).energy)
					/ point.distanceSq(e);

			int closer = 0;
			Enumeration enum2 = enemies.elements();
			do
				if (.9 * e.distance((EnemyInfo) enum2.nextElement()) > e.distance(point))
					closer++;
			while (enum2.hasMoreElements());
			if (!e.isTeammate && (closer <= 1 || e.lastHit > bot.getTime() - 200 || e == currentTarget))
				thisrisk *= 1 + Math.abs(Math.cos(AdditionalUtils.absoluteBearing(myLocation, point) - AdditionalUtils.absoluteBearing(e, myLocation)));
			risk += thisrisk;
		} while (enum1.hasMoreElements());
		return risk;
	}

	@Override
	public void exit() {
		// TODO Auto-generated method stub

	}

	public static double targetability(EnemyInfo e) {
		return myLocation.distance(e) - e.energy;
	}

	public void onHitByBullet(HitByBulletEvent e) {
		try {
			// find out when we got shot and by what bot
			((EnemyInfo) enemies.get(e.getName())).lastHit = bot.getTime();
		} catch (NullPointerException ex) {
		}
	}

	public void onRobotDeath(RobotDeathEvent e) {
		// remove dead bots from list
		if (currentTarget == enemies.remove(e.getName()))
			currentTarget = null;
	}

	@Override
	public void onScannedRobot(ScannedRobotEvent e) {
		if (bot.getOthers() < 2) {
			machine.changeState(new WaveSurfer(bot));
		}
		String name;
		EnemyInfo enemy = (EnemyInfo) enemies.get(name = e.getName());
		Point2D.Double myLocation = new Point2D.Double(bot.getX(), bot.getY());
		if (enemy == null) {
			enemies.put(name, enemy = new EnemyInfo());
			gravpoints.add(AdditionalUtils.project(myLocation,
					AdditionalUtils.absoluteBearing(myLocation,
							AdditionalUtils.project(myLocation, bot.getHeadingRadians() + e.getBearingRadians(), e.getDistance())),
					e.getDistance()));
		}

		enemy.energy = e.getEnergy();
		enemy.setLocation(AdditionalUtils.project(myLocation, bot.getHeadingRadians() + e.getBearingRadians(), e.getDistance()));
		if ((currentTarget == null || targetability(enemy) < targetability(currentTarget) - 100))
			currentTarget = enemy;

		// this did the magic trick
		antiGravMove();

	}

	public void radar(ScannedRobotEvent e) {
		double bearing = e.getBearingRadians() + bot.getHeadingRadians();
		double radarTurn = Utils.normalRelativeAngle(bearing - bot.getRadarHeadingRadians());
		double extraTurn = Math.min(Math.atan(36.0 / e.getDistance()), Rules.RADAR_TURN_RATE_RADIANS);
		if (radarTurn < 0)
			radarTurn -= extraTurn;
		else
			radarTurn += extraTurn;

		bot.setTurnRadarRightRadians(radarTurn);
	}
}

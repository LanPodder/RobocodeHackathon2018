package de.metro.robocode;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class WaveSurfer implements IState {
    public static int BINS = 47;
    public static double surfStats[] = new double[BINS];
    public Point2D.Double myLocation;
    public Point2D.Double enemyLocation;

    public ArrayList enemyWaves;
    public ArrayList surfDirections;
    public ArrayList surfAbsBearings;
    List<Wave> waves = new ArrayList<Wave>();
    static int[] stats = new int[31];
    int direction;
    StateMachine m = new StateMachine();

    public static double _oppEnergy = 100.0;

    public static Rectangle2D.Double _fieldRect = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 564);
    public static double WALL_STICK = 160;

    AdvancedRobot bot;

    public WaveSurfer(AdvancedRobot bot) {
        this.bot = bot;

    }

    @Override
    public void enter() {
        enemyWaves = new ArrayList();
        surfDirections = new ArrayList();
        surfAbsBearings = new ArrayList();
        m.changeState(new OneOnOneRadar(bot));
    }

    @Override
    public void doState() {
        m.executeStateUpdate();
    }

    @Override
    public void exit() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        myLocation = new Point2D.Double(bot.getX(), bot.getY());
        m.excecuteOnScannedRobot(e);

        double lateralVelocity = bot.getVelocity() * Math.sin(e.getBearingRadians());
        double absBearing = e.getBearingRadians() + bot.getHeadingRadians();
        surfDirections.add(0, new Integer((lateralVelocity >= 0) ? 1 : -1));
        surfAbsBearings.add(0, new Double(absBearing + Math.PI));

        double bulletPower = _oppEnergy - e.getEnergy();
        if (bulletPower < 3.01 && bulletPower > 0.09 && surfDirections.size() > 2) {
            EnemyWave ew = new EnemyWave();
            ew.fireTime = bot.getTime() - 1;
            ew.bulletVelocity = bulletVelocity(bulletPower);
            ew.distanceTraveled = bulletVelocity(bulletPower);
            ew.direction = ((Integer) surfDirections.get(2)).intValue();
            ew.directAngle = ((Double) surfAbsBearings.get(2)).doubleValue();
            ew.fireLocation = (Point2D.Double) enemyLocation.clone(); // last tick

            enemyWaves.add(ew);
        }

        _oppEnergy = e.getEnergy();

        // update after EnemyWave detection, because that needs the previous
        // enemy location as the source of the wave
        enemyLocation = AdditionalUtils.project(myLocation, absBearing, e.getDistance());

        for (int i = 0; i < waves.size(); i++) {
            Wave currentWave = (Wave) waves.get(i);
            if (currentWave.checkHit(enemyLocation.x, enemyLocation.y, bot.getTime())) {
                waves.remove(currentWave);
                i--;
            }
        }

        double power = Math.min(3, Math.max(.1, Math.min(e.getEnergy(), 400 / e.getDistance())));
        if (e.getVelocity() != 0) {
            if (Math.sin(e.getHeadingRadians() - absBearing) * e.getVelocity() < 0)
                direction = -1;
            else
                direction = 1;
        }
        int[] currentStats = stats;// [(int)e.getDistance()/100];
        Wave newWave = new Wave(bot.getX(), bot.getY(), absBearing, power, direction, bot.getTime(), currentStats);

        // straight forward: go through currentstats and get the index with the highest
        // value (the best one: most likely to hit)
        int bestindex = 15; // initialize it to be in the middle, guessfactor 0.
        for (int i = 0; i < 31; i++)
            if (currentStats[bestindex] < currentStats[i])
                bestindex = i;

        // this should do the opposite of the math in the WaveBullet:
        double guessfactor = (double) (bestindex - (stats.length - 1) / 2) / ((stats.length - 1) / 2);
        double angleOffset = direction * guessfactor * newWave.maxEscapeAngle();
        double gunAdjust = Utils.normalRelativeAngle(absBearing - bot.getGunHeadingRadians() + angleOffset);
        bot.setTurnGunRightRadians(gunAdjust);

        if (bot.setFireBullet(power) != null) {
            waves.add(newWave);
        }
        // *****
        updateWaves();
        doSurfing();
    }

    public void radar(ScannedRobotEvent e) {
        double bearing = e.getBearingRadians() + bot.getHeadingRadians();
        double radarTurn = Utils.normalRelativeAngle(bearing - bot.getRadarHeadingRadians());

        // Distance we want to scan from middle of enemy to either side
        // The 36.0 is how many units from the center of the enemy robot it scans.
        double extraTurn = Math.min(Math.atan(36.0 / e.getDistance()), Rules.RADAR_TURN_RATE_RADIANS);

        if (radarTurn < 0)
            radarTurn -= extraTurn;
        else
            radarTurn += extraTurn;

        // Turn the radar
        bot.setTurnRadarRightRadians(radarTurn);
    }

    public void updateWaves() {
        for (int x = 0; x < enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave) enemyWaves.get(x);

            ew.distanceTraveled = (bot.getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled > myLocation.distance(ew.fireLocation) + 50) {
                enemyWaves.remove(x);
                x--;
            }
        }
    }

    public EnemyWave getClosestSurfableWave() {
        double closestDistance = 50000; // I juse use some very big number here
        EnemyWave surfWave = null;

        for (int x = 0; x < enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave) enemyWaves.get(x);
            double distance = myLocation.distance(ew.fireLocation) - ew.distanceTraveled;

            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }

        return surfWave;
    }

    public static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (AdditionalUtils.absoluteBearing(ew.fireLocation, targetLocation) - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle) / maxEscapeAngle(ew.bulletVelocity) * ew.direction;

        return (int) limit(0, (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2), BINS - 1);
    }

    public void logHit(EnemyWave ew, Point2D.Double targetLocation) {
        int index = getFactorIndex(ew, targetLocation);

        for (int x = 0; x < BINS; x++) {

            surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {

        if (!enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            for (int x = 0; x < enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave) enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled - myLocation.distance(ew.fireLocation)) < 50
                        && Math.abs(bulletVelocity(e.getBullet().getPower()) - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }

            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation);

                enemyWaves.remove(enemyWaves.lastIndexOf(hitWave));
            }
        }
    }

    public Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
        Point2D.Double predictedPosition = (Point2D.Double) myLocation.clone();
        double predictedVelocity = bot.getVelocity();
        double predictedHeading = bot.getHeadingRadians();
        double maxTurning, moveAngle, moveDir;

        int counter = 0; // number of ticks in the future
        boolean intercepted = false;

        do {
            moveAngle = wallSmoothing(predictedPosition,
                    AdditionalUtils.absoluteBearing(surfWave.fireLocation, predictedPosition)
                            + (direction * (Math.PI / 2)),
                    direction) - predictedHeading;
            moveDir = 1;

            if (Math.cos(moveAngle) < 0) {
                moveAngle += Math.PI;
                moveDir = -1;
            }

            moveAngle = Utils.normalRelativeAngle(moveAngle);

            maxTurning = Math.PI / 720d * (40d - 3d * Math.abs(predictedVelocity));
            predictedHeading = Utils.normalRelativeAngle(predictedHeading + limit(-maxTurning, moveAngle, maxTurning));

            predictedVelocity += (predictedVelocity * moveDir < 0 ? 2 * moveDir : moveDir);
            predictedVelocity = limit(-8, predictedVelocity, 8);

            // calculate the new predicted position
            predictedPosition = AdditionalUtils.project(predictedPosition, predictedHeading, predictedVelocity);

            counter++;

            if (predictedPosition.distance(surfWave.fireLocation) < surfWave.distanceTraveled
                    + (counter * surfWave.bulletVelocity) + surfWave.bulletVelocity) {
                intercepted = true;
            }
        } while (!intercepted && counter < 500);

        return predictedPosition;
    }

    public double checkDanger(EnemyWave surfWave, int direction) {
        int index = getFactorIndex(surfWave, predictPosition(surfWave, direction));

        return surfStats[index];
    }

    public void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();

        if (surfWave == null) {
            return;
        }

        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);

        double goAngle = AdditionalUtils.absoluteBearing(surfWave.fireLocation, myLocation);
        if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(myLocation, goAngle - (Math.PI / 2), -1);
            // bulletShield(surfWave, -1);
        } else {
            goAngle = wallSmoothing(myLocation, goAngle + (Math.PI / 2), 1);
            // bulletShield(surfWave, 1);
        }

        setBackAsFront(bot, goAngle);
    }
    // bullet shadow

    public void bulletShield(EnemyWave surfwave, int orientation) {
        double ultimateShootingAngle = 0;
        if (bot.getGunHeat() <= 1) {
            ultimateShootingAngle = Utils.normalRelativeAngle(surfwave.directAngle + orientation * (Math.PI / 2));
        }
        bot.setTurnGunLeft(ultimateShootingAngle);
        bot.fire(0.1);
    }

    public double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
        while (!_fieldRect.contains(AdditionalUtils.project(botLocation, angle, 160))) {
            angle += orientation * 0.05;
        }
        return angle;
    }

    public static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static double bulletVelocity(double power) {
        return (20D - (3D * power));
    }

    public static double maxEscapeAngle(double velocity) {
        return Math.asin(8.0 / velocity);
    }

    public static void setBackAsFront(AdvancedRobot robot, double goAngle) {
        double angle = Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());
        if (Math.abs(angle) > (Math.PI / 2)) {
            if (angle < 0) {
                robot.setTurnRightRadians(Math.PI + angle);
            } else {
                robot.setTurnLeftRadians(Math.PI - angle);
            }
            robot.setBack(100);
        } else {
            if (angle < 0) {
                robot.setTurnLeftRadians(-1 * angle);
            } else {
                robot.setTurnRightRadians(angle);
            }
            robot.setAhead(100);
        }
    }

}

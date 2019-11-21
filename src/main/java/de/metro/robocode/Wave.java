package de.metro.robocode;

import java.awt.geom.*;
import robocode.util.*;

public class Wave {
    // start position we fire from
    private double startX, startY;
    // angle we fire from. to know where 0 would be in order to calculate max escape
    // angle
    private double startBearing;
    private double power; // bullet speed
    // current time we fire
    private long fireTime;

    // enemys clock direction relative to us (1 clockwise -1 counter)
    private int direction;

    private int[] returnSegment;

    public Wave(double x, double y, double bearing, double power, int direction, long time, int[] segment) {
        startX = x;
        startY = y;
        startBearing = bearing;
        this.power = power;
        fireTime = time;
        this.direction = direction;
        returnSegment = segment;
    }

    public double getBulletSpeed() {
        return 20 - power * 3;
    }

    public double maxEscapeAngle() {
        return Math.asin(8 / getBulletSpeed());
    }

    // TODO double check this for better understanding
    public boolean checkHit(double enemyX, double enemyY, long currentTime) {

        if (Point2D.distance(startX, startY, enemyX, enemyY) <= (currentTime - fireTime) * getBulletSpeed()) {
            double desiredDirection = Math.atan2(enemyX - startX, enemyY - startY);
            double angleOffset = Utils.normalRelativeAngle(desiredDirection - startBearing);
            double guessFactor = Math.max(-1, Math.min(1, angleOffset / maxEscapeAngle())) * direction;
            int index = (int) Math.round((returnSegment.length - 1) / 2 * (guessFactor + 1));
            returnSegment[index]++;
            return true;
        }
        return false;
    }
}
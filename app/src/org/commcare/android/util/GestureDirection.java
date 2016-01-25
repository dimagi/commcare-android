package org.commcare.android.util;

/**
 * Calculates the direction of a gesture/fling. Used by {@link GestureDetector}
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class GestureDirection {

    public enum UserGesture {
        SWIPE_RIGHT, SWIPE_LEFT, SWIPE_UP, SWIPE_DOWN, SWIPE_UNKNOWN
    }

    private final static int MAX_DISTANCE = 40;

    private final float mStartX;
    private final float mStartY;

    private float mEndX;
    private float mEndY;


    public GestureDirection(float x, float y) {
        mStartX = x;
        mStartY = y;
        mEndX = x;
        mEndY = y;
    }


    public void updateEndPoint(float x, float y) {
        mEndX = x;
        mEndY = y;
    }

    public UserGesture getDirection() {
        float dx = mEndX - mStartX;
        float dy = mEndY - mStartY;
        double distance = Math.hypot(dx, dy);

        if (distance < MAX_DISTANCE) {
            return UserGesture.SWIPE_UNKNOWN;
        }
        double angle = Math.acos(dx / distance);
        double limit = Math.PI / 6;
        if ((angle < limit || (angle > (Math.PI - limit)))) {
            if (dx > 0)
                return UserGesture.SWIPE_RIGHT;
            else
                return UserGesture.SWIPE_LEFT;

        }
        if ((angle > 2 * limit) && angle < 4 * limit) {
            if (dy > 0)
                return UserGesture.SWIPE_DOWN;
            else
                return UserGesture.SWIPE_UP;
        }
        return UserGesture.SWIPE_UNKNOWN;
    }
}

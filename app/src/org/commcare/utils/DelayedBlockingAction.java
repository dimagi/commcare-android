package org.commcare.utils;

import android.support.annotation.NonNull;

/**
 * A delayed blocking action will be executed at most one time after some fixed period of delay.
 *
 * Each action has an associated tag. This tag should always represent the same action, and the
 * caller should be indifferent as to whether their delayed action runnable fires, or whether
 * another action fires at another time. If the associated manager receives multiple actions in
 * sequence it will invalidate the old actions in order, assuming that the request for a delay
 * has restarted
 *
 * An example of an implementing action is a screen update that should fire after a user sets the
 * value of some interactive widget. Setting a short delay (200ms or so) can ensure that as the user
 * interacts with a value that can change rapidly (like a slider) that the value has a chance to
 * "settle" before processing, rather than firing a barrage of value update events.
 *
 * Delayed actions time out after a fixed period
 *
 * Created by ctsims on 8/26/2016.
 */
public abstract class DelayedBlockingAction implements Runnable {

    @NonNull
    private final Object tag;

    private final int delay;

    private final int timeout;

    private final long timeInitiated;

    private boolean invalidated = false;

    private boolean fired = false;

    private final Object lock = new Object();

    public DelayedBlockingAction(Object tag, int delay) {
        this(tag, delay, 2000);
    }

    public DelayedBlockingAction(Object tag, int delay, int timeout) {
        this.tag = tag;
        this.delay = delay;
        this.timeout = timeout;
        this.timeInitiated = System.currentTimeMillis();

    }

    @Override
    public final void run() {
        synchronized (lock){
            if (invalidated || fired) {
                return;
            }
            fired = true;
        }
        runAction();
    }

    /**
     * Performs the actual action
     */
    protected abstract void runAction();


    public boolean isSameType(DelayedBlockingAction action) {
        return this.tag.equals(action.tag);
    }

    /**
     * Signal to this action that it should no longer execute itself when it is triggered. Generally
     * this is done because a different action has taken its place, or the context is no longer
     * available to receive the action.
     *
     * @return false if the action could not be prevented due to having already triggered.
     */
    public boolean invalidate() {
        synchronized (lock) {
            if (this.fired) {
                return false;
            } else {
                this.invalidated = true;
                return true;
            }
        }
    }

    public boolean isPending() {
        synchronized (lock) {
            if (!this.fired && hasTimedOut()) {
                this.invalidated = true;
            }
            return !(this.fired || this.invalidated);
        }
    }

    protected boolean hasTimedOut() {
        long now = System.currentTimeMillis();
        return (timeInitiated + timeout) < now;
    }

    public int getDelay() {
        return delay;
    }
}

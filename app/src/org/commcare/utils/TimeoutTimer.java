package org.commcare.utils;

import android.os.CountDownTimer;

import org.commcare.interfaces.TimerListener;

/**
 * Calls back to a TimerListener after a set amount of time
 *
 * @author wspride
 */
public class TimeoutTimer extends CountDownTimer {
    private final TimerListener mTimerListener;
    private long mUntilFinished;

    public TimeoutTimer(long millisInFuture, TimerListener tl) {
        super(millisInFuture, 1000);
        mTimerListener = tl;
    }

    @Override
    public void onFinish() {
        mUntilFinished = 0;
        mTimerListener.notifyTimerFinished();
    }

    @Override
    public void onTick(long millisUntilFinished) {
        mUntilFinished = millisUntilFinished;
    }

    public long getMillisUntilFinished() {
        return mUntilFinished;
    }
}

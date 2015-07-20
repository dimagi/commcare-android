package org.odk.collect.android.utilities;

import org.odk.collect.android.listeners.TimerListener;

import android.os.CountDownTimer;

/**
 * simple class that calls back to a TimerListener after a set amount of time
 * 
 * @author wspride
 *
 */

public class ODKTimer extends CountDownTimer{
    TimerListener mTimerListener;
    long mUntilFinished;

    public ODKTimer(long millisInFuture, TimerListener tl) {
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
    
    public long getMillisUntilFinished(){
        return mUntilFinished;
    }
}

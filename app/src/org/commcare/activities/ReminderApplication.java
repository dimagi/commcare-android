package org.commcare.activities;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * @author ctsims
 *
 */
public class ReminderApplication extends Application {
    
    private static ReminderApplication singletonInstance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        singletonInstance = this;
    }
    
    public static ReminderApplication _() {
        return singletonInstance;
    }

    //Start Service code. Will be changed in the future
    private CommCareReminderService mBoundService;

    private ServiceConnection mConnection;

    private Object serviceLock = new Object();
    boolean mIsBound = false;
    boolean mIsBinding = false;
    
    public void launchService() {
        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                synchronized(serviceLock) {
                    mBoundService = ((CommCareReminderService.LocalBinder)service).getService();
                    
                    //service available
                    mIsBound = true;
                    
                    //Don't signal bind completion until the db is initialized.
                    mIsBinding = false;
                    mBoundService.showNotice();
                }
            }


            public void onServiceDisconnected(ComponentName className) {
                // This is called when the connection with the service has been
                // unexpectedly disconnected -- that is, its process crashed.
                // Because it is running in our same process, we should never
                // see this happen.
                mBoundService = null;
            }
        };
        
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        super.bindService(new Intent(this,  CommCareReminderService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBinding = true;
    }
    
    public void stopService() {
       synchronized(serviceLock) {
           if(mIsBound) {
               mBoundService.stop();
               mIsBound = false;
           }
       }
    }
    
    public boolean isServiceRunning() {
        synchronized(serviceLock) {
            return mIsBound;
        }
    }

}

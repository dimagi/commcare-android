package org.commcare.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;

// Broadcast Receiver to log out a CommCare user
public class CommCareLogoutReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        CommCareApplication.instance().closeUserSession();
    }
}

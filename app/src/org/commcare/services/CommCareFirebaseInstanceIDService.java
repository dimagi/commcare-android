package org.commcare.services;

import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Created by Saumya on 6/29/2016.
 * Refreshes device's instanceid when needed.
 * Will eventually be responsible for updating server with the new reg token for a given device.
 */
public class CommCareFirebaseInstanceIDService extends FirebaseInstanceIdService{


    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d( "Refreshed token: ",  refreshedToken);

        sendRegistrationToServer(refreshedToken);
    }

    private void sendRegistrationToServer(String token){
        ComponentName cn = new ComponentName("dalvik.commcare.org.commcaredevelopertoolkit", "dalvik.commcare.org.commcaredevelopertoolkit.activities.TokenRefreshService");
        Intent i = new Intent();
        i.setComponent(cn);
        startService(i);
    }
}

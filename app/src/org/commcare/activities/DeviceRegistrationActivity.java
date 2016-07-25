package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.google.firebase.iid.FirebaseInstanceId;

/**
 * Activity that passes FCM registration token back to Dev Toolkit
 */
public class DeviceRegistrationActivity extends Activity {

    private String registrationToken;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        FirebaseInstanceId instanceID = FirebaseInstanceId.getInstance();
        final FirebaseInstanceId copy = instanceID;

        new Thread(new Runnable() {
            public void run() {
                try{
                    registrationToken = copy.getToken("139642101642", "GCM"); //Change the sender ID and make secret eventually
                    Intent intent = getIntent();
                    intent.putExtra("TOKEN", registrationToken);
                    setResult(RESULT_OK, intent);
                    finish();
                }catch(Exception e){
                    Intent intent = getIntent();
                    setResult(RESULT_CANCELED, intent);
                    e.printStackTrace();
                    finish();
                }
            }
        }).start();
    }
}

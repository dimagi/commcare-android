package org.commcare.dalvik.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * Created by amstone326 on 1/18/16.
 */
public class PinAuthenticationActivity extends
        SessionAwareCommCareActivity<PinAuthenticationActivity> {

    private static final String TAG = PinAuthenticationActivity.class.getSimpleName();

    public static final String PASSWORD_FROM_AUTH = "password-obtained-from-auth";

    private LoginActivity.LoginMode authMode;
    private UserKeyRecord currentRecord;
    private String passwordObtainedFromAuth;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRecordAndAuthMode();
    }

    private void setupUI() {
        //TODO: Create view and refresh UI based on the auth mode
    }

    private void setRecordAndAuthMode() {
        currentRecord = CommCareApplication._().getRecordForCurrentUser();
        if (currentRecord == null) {
            Log.i(TAG, "Something went wrong in PinAuthenticationActivity. Could not find the " +
                    "current user record, so just finishing the activity");
            setResult(RESULT_CANCELED);
            this.finish();
            return;
        }

        if (currentRecord.hasPinSet()) {
            // If a PIN is already set and the user is trying to change it, we can have them
            // enter that, and then use it to get the password
            authMode = LoginActivity.LoginMode.PIN;
        } else {
            // Otherwise, we're going to need them to enter their password
            authMode = LoginActivity.LoginMode.PASSWORD;
        }

        setupUI();
    }

    private void onSuccessfulAuth() {
        Intent i = new Intent();
        i.putExtra(PASSWORD_FROM_AUTH, passwordObtainedFromAuth);
        setResult(RESULT_OK, i);
        finish();
    }

}

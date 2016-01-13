package org.commcare.dalvik.activities;

import android.os.Bundle;

import org.commcare.android.framework.SessionAwareCommCareActivity;

/**
 * Created by amstone326 on 1/12/16.
 */
public class CreatePinActivity extends SessionAwareCommCareActivity<CreatePinActivity> {

    private String userPassword;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.userPassword = getIntent().getStringExtra(LoginActivity.PASSWORD_FROM_LOGIN);
        if (userPassword == null) {
            // Failed to pass along the user password somehow; cannot proceed without it
            setResult(RESULT_CANCELED);
            finish();
        }
    }

}

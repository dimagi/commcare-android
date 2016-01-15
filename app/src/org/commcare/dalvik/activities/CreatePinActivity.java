package org.commcare.dalvik.activities;

import android.os.Bundle;

import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.User;

/**
 * Created by amstone326 on 1/12/16.
 */
public class CreatePinActivity extends SessionAwareCommCareActivity<CreatePinActivity> {

    private String unhashedUserPassword;
    private LoginActivity.LoginMode loginMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.loginMode = LoginActivity.LoginMode.fromString(
                getIntent().getStringExtra(LoginActivity.LOGIN_MODE));
        this.unhashedUserPassword = getIntent().getStringExtra(LoginActivity.PASSWORD_FROM_LOGIN);

        setupView();
    }

    private void setupView() {
        if (loginMode == LoginActivity.LoginMode.PRIMED) {
            // ONLY option is to set pin
        } else {
            // show all 3 options (set pin, don't show again, ask me next time)
        }
    }

    private void assignPin(String pin) {
        try {
            User currentUser = CommCareApplication._().getSession().getLoggedInUser();
            String username = currentUser.getUsername();
            String passwordHash = currentUser.getPasswordHash();
            UserKeyRecord currentUserRecord = null;
            for (UserKeyRecord record :
                    CommCareApplication._().getCurrentApp().getStorage(UserKeyRecord.class)) {
                if (record.getUsername().equals(username) && record.getPasswordHash().equals(passwordHash)) {
                    currentUserRecord = record;
                    break;
                }
            }
            if (currentUserRecord == null) {
                // Failed to find a ukr corresponding to this user; cannot proceed without it
                setResult(RESULT_CANCELED);
                finish();
            }
            currentUserRecord.assignPinToRecord(pin, unhashedUserPassword);
        } catch (SessionUnavailableException e) {

        }
    }

}

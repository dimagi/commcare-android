package org.commcare.dalvik.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.framework.UiElement;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.User;

import java.util.Vector;

/**
 * Created by amstone326 on 1/12/16.
 */
@ManagedUi(R.layout.create_pin_view)
public class CreatePinActivity extends SessionAwareCommCareActivity<CreatePinActivity> {

    @UiElement(value=R.id.pin_entry)
    private EditText enterPinBox;

    @UiElement(value=R.id.pin_prompt_text)
    private TextView promptText;

    @UiElement(value=R.id.pin_cancel_button)
    private Button cancelButton;

    @UiElement(value=R.id.pin_confirm_button)
    private Button continueButton;

    private static final String TAG = CreatePinActivity.class.getSimpleName();

    private String unhashedUserPassword;
    private LoginActivity.LoginMode loginMode;
    private UserKeyRecord userRecord;

    // Indicates whether the user is entering their PIN for the first time, or is confirming it
    private boolean inConfirmMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loginMode = LoginActivity.LoginMode.fromString(
                getIntent().getStringExtra(LoginActivity.LOGIN_MODE));
        userRecord = getRecordForCurrentUser();
        if (userRecord == null) {
            Log.i(TAG, "Something went wrong in CreatePinActivity. Could not get a matching user " +
                    "record, so just finishing the activity");
            setResult(RESULT_CANCELED);
            this.finish();
            return;
        }

        if (loginMode == LoginActivity.LoginMode.PASSWORD) {
            unhashedUserPassword = getIntent().getStringExtra(LoginActivity.PASSWORD_FROM_LOGIN);
        } else {
            unhashedUserPassword = userRecord.getPrimedPassword();
            userRecord.clearPrimedPassword();
        }

        setButtonListeners();
    }

    private void setButtonListeners() {
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!inConfirmMode) {
                    processInitialPinEntry();
                } else {
                    processConfirmPinEntry();
                }
            }
        });
    }

    private void processInitialPinEntry() {
        String enteredPin = enterPinBox.getText().toString();
        if (enteredPin.length() < 4) {
            // Set prompt text to error message
        } else {

        }
    }

    private void processConfirmPinEntry() {

    }

    private UserKeyRecord getRecordForCurrentUser() {
        User currentUser;
        try {
            currentUser = CommCareApplication._().getSession().getLoggedInUser();
        } catch (SessionUnavailableException e) {
            Log.i(TAG, "Something went wrong in CreatePinActivity. There was no logged in user, " +
                    "so just finishing the activity");
            setResult(RESULT_CANCELED);
            this.finish();
            return null;
        }

        Vector<UserKeyRecord> matchingRecords =
                CommCareApplication._().getCurrentApp().getStorage(UserKeyRecord.class)
                        .getRecordsForValue(UserKeyRecord.META_USERNAME, currentUser.getUsername());

        for (UserKeyRecord record : matchingRecords) {
            if (record.isPasswordValid(unhashedUserPassword)) {
                return record;
            }
        }
        return null;
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

package org.commcare.dalvik.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.framework.UiElement;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.locale.Localization;

/**
 * When a user selects Set/Reset PIN from the home activity options menu, they must first
 * authenticate themselves in order to be able to do so. This is the activity that launches to
 * prompt the user to enter either their current PIN, if it exists, or their password if it does
 * not, before proceeding to the CreatePinActivity
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
@ManagedUi(R.layout.pin_auth_view)
public class PinAuthenticationActivity extends
        SessionAwareCommCareActivity<PinAuthenticationActivity> {

    @UiElement(R.id.pin_prompt_text)
    private TextView promptText;

    @UiElement(R.id.pin_entry)
    private EditText pinEntry;

    @UiElement(R.id.password_entry)
    private EditText passwordEntry;

    @UiElement(value=R.id.pin_confirm_button, locale="pin.auth.enter.button")
    private Button enterButton;

    @UiElement(R.id.pin_cancel_button)
    private Button cancelButton;

    private static final String TAG = PinAuthenticationActivity.class.getSimpleName();

    public static final String PASSWORD_FROM_AUTH = "password-obtained-from-auth";

    private LoginMode authMode;
    private UserKeyRecord currentRecord;
    private String passwordObtainedFromAuth;

    @Override
    protected void onCreateAware(Bundle savedInstanceState) throws SessionUnavailableException {
        if (!setRecordAndAuthMode()) {
            return;
        }
        setupUI();
    }

    /**
     * User the information in the current user's UKR to determine what auth mode we should be in
     *
     * @return If the call completed successfully
     */
    private boolean setRecordAndAuthMode() throws SessionUnavailableException {
        currentRecord = CommCareApplication._().getRecordForCurrentUser();
        if (currentRecord == null) {
            Log.i(TAG, "Something went wrong in PinAuthenticationActivity. Could not find the " +
                    "current user record, so just finishing the activity");
            setResult(RESULT_CANCELED);
            this.finish();
            return false;
        }

        if (currentRecord.hasPinSet()) {
            // If a PIN is already set and the user is trying to change it, we can have them
            // enter that, and then use it to get the password
            authMode = LoginMode.PIN;
        } else {
            // Otherwise, we're going to need them to enter their password
            authMode = LoginMode.PASSWORD;
        }
        return true;
    }

    private void setupUI() {
        if (authMode == LoginMode.PASSWORD) {
            setPasswordAuthModeUI();
        } else {
            setPinAuthModeUI();
        }

        pinEntry.addTextChangedListener(CreatePinActivity.getPinTextWatcher(enterButton));

        enterButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (authMode == LoginMode.PASSWORD) {
                    checkEnteredPassword();
                } else {
                    checkEnteredPin();
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }

    private void setPinAuthModeUI() {
        promptText.setText(Localization.get("pin.auth.prompt.pin"));
        pinEntry.setVisibility(View.VISIBLE);
        passwordEntry.setVisibility(View.GONE);
        enterButton.setEnabled(false);
    }

    private void setPasswordAuthModeUI() {
        promptText.setText(Localization.get("pin.auth.prompt.password"));
        passwordEntry.setVisibility(View.VISIBLE);
        pinEntry.setVisibility(View.GONE);
        enterButton.setEnabled(true);
    }

    private void checkEnteredPassword() {
        String enteredPassword = passwordEntry.getText().toString();
        if (currentRecord.isPasswordValid(enteredPassword)) {
            passwordObtainedFromAuth = enteredPassword;
            onSuccessfulAuth();
        } else {
            onUnsuccessfulAuth();
        }
    }

    private void checkEnteredPin() {
        String enteredPin = pinEntry.getText().toString();
        if (currentRecord.isPinValid(enteredPin)) {
            passwordObtainedFromAuth = currentRecord.getUnhashedPasswordViaPin(enteredPin);
            onSuccessfulAuth();
        } else {
            onUnsuccessfulAuth();
        }
    }

    private void onUnsuccessfulAuth() {
        if (authMode == LoginMode.PIN) {
            Toast.makeText(this, Localization.get("pin.auth.failed.pin"), Toast.LENGTH_LONG).show();
            pinEntry.setText("");
        } else {
            Toast.makeText(this, Localization.get("pin.auth.failed.password"), Toast.LENGTH_LONG).show();
            passwordEntry.setText("");
        }
    }

    private void onSuccessfulAuth() {
        Intent i = new Intent();
        i.putExtra(PASSWORD_FROM_AUTH, passwordObtainedFromAuth);
        setResult(RESULT_OK, i);
        finish();
    }

}

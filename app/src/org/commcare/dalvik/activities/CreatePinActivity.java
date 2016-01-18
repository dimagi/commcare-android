package org.commcare.dalvik.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.framework.UiElement;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;

import java.util.Vector;

/**
 * Created by amstone326 on 1/12/16.
 */
@ManagedUi(R.layout.create_pin_view)
public class CreatePinActivity extends SessionAwareCommCareActivity<CreatePinActivity> {

    private static final int MENU_REMEMBER_PW_AND_LOGOUT = Menu.FIRST;

    @UiElement(value=R.id.pin_entry)
    private EditText enterPinBox;

    @UiElement(value=R.id.pin_prompt_text)
    private TextView promptText;

    @UiElement(value=R.id.pin_cancel_button)
    private Button cancelButton;

    @UiElement(value=R.id.pin_confirm_button)
    private Button continueButton;

    private static final String TAG = CreatePinActivity.class.getSimpleName();
    public static final String CHOSE_REMEMBER_PASSWORD = "chose-remember-password";

    private String unhashedUserPassword;
    private LoginActivity.LoginMode loginMode;
    private UserKeyRecord userRecord;

    // Indicates whether the user is entering their PIN for the first time, or is confirming it
    private boolean inConfirmMode;
    private String firstRoundPin;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loginMode = LoginActivity.LoginMode.fromString(
                getIntent().getStringExtra(LoginActivity.LOGIN_MODE));

        if (loginMode == LoginActivity.LoginMode.PASSWORD) {
            unhashedUserPassword = getIntent().getStringExtra(LoginActivity.PASSWORD_FROM_LOGIN);
        }

        userRecord = getRecordForCurrentUser();
        if (userRecord == null) {
            Log.i(TAG, "Something went wrong in CreatePinActivity. Could not get a matching user " +
                    "record, so just finishing the activity");
            setResult(RESULT_CANCELED);
            this.finish();
            return;
        }

        if (loginMode == LoginActivity.LoginMode.PRIMED) {
            unhashedUserPassword = userRecord.getPrimedPassword();
            userRecord.clearPrimedPassword();
        }

        setListeners();
        setInitialEntryMode();
    }

    private void setListeners() {
        enterPinBox.addTextChangedListener(getPinTextWatcher());

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

    private TextWatcher getPinTextWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 4) {
                    continueButton.setEnabled(true);
                } else {
                    continueButton.setEnabled(false);
                }
            }
        };
    }

    private void processInitialPinEntry() {
        firstRoundPin = enterPinBox.getText().toString();
        setConfirmMode();
    }

    private void processConfirmPinEntry() {
        String enteredPin = enterPinBox.getText().toString();
        if (enteredPin.equals(firstRoundPin)) {
            assignPin(enteredPin);
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, getString(R.string.pins_dont_match), Toast.LENGTH_LONG).show();
            setInitialEntryMode();
        }
    }

    private void setInitialEntryMode() {
        enterPinBox.setText("");
        enterPinBox.requestFocus();
        continueButton.setText(getString(R.string.continue_pin_button));
        promptText.setText(getString(R.string.enter_pin_directive));
        inConfirmMode = false;
    }

    private void setConfirmMode() {
        enterPinBox.setText("");
        enterPinBox.requestFocus();
        continueButton.setText(getString(R.string.confirm_pin_button));
        promptText.setText(getString(R.string.confirm_pin_directive));
        inConfirmMode = true;
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

        String username = currentUser.getUsername();
        for (UserKeyRecord record :
                CommCareApplication._().getCurrentApp().getStorage(UserKeyRecord.class)
                        .getRecordsForValue(UserKeyRecord.META_USERNAME, username)) {
            if (loginMode == LoginActivity.LoginMode.PASSWORD) {
                if (record.isPasswordValid(this.unhashedUserPassword)) {
                    return record;
                }
            } else {
                // primed mode
                if (record.isPrimedForNextLogin()) {
                    return record;
                }
            }
        }
        return null;
    }

    private void assignPin(String pin) {
        userRecord.assignPinToRecord(pin, unhashedUserPassword);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_REMEMBER_PW_AND_LOGOUT, 0,
                Localization.get("remember.password.for.next.login"));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_REMEMBER_PW_AND_LOGOUT) {
            userRecord.setPrimedPassword(unhashedUserPassword);
            Intent i = new Intent();
            i.putExtra(CHOSE_REMEMBER_PASSWORD, true);
            setResult(RESULT_OK, i);
            finish();
        }
        return true;
    }

}

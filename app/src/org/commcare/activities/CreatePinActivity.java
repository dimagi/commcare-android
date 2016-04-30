package org.commcare.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.AlertDialogFactory;
import org.javarosa.core.services.locale.Localization;

/**
 * Activity that allows a user to set or reset their auth PIN
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
@ManagedUi(R.layout.create_pin_view)
public class CreatePinActivity extends SessionAwareCommCareActivity<CreatePinActivity> {

    private static final int MENU_REMEMBER_PW_AND_LOGOUT = Menu.FIRST;

    @UiElement(value = R.id.pin_entry)
    private EditText enterPinBox;

    @UiElement(value = R.id.pin_prompt_text)
    private TextView promptText;

    @UiElement(value = R.id.pin_cancel_button)
    private Button cancelButton;

    @UiElement(value = R.id.pin_confirm_button)
    private Button continueButton;

    @UiElement(value = R.id.extra_msg, locale = "pin.primed.mode.message")
    private TextView primedModeMessage;

    public static final String CHOSE_REMEMBER_PASSWORD = "chose-remember-password";

    private static final String WAS_IN_CONFIRM_MODE = "was-in-confirm-mode";
    private static final String FIRST_ROUND_PIN = "first-round-pin";

    private String unhashedUserPassword;
    private UserKeyRecord userRecord;

    // Indicates whether the user is entering their PIN for the first time, or is confirming it
    private boolean inConfirmMode;
    private String firstRoundPin;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) throws SessionUnavailableException {
        super.onCreateSessionSafe(savedInstanceState);

        userRecord = CommCareApplication._().getRecordForCurrentUser();
        unhashedUserPassword = CommCareApplication._().getSession().getLoggedInUser().getCachedPwd();
        LoginMode loginMode = (LoginMode)getIntent().getSerializableExtra(LoginActivity.LOGIN_MODE);

        if (loginMode == LoginMode.PRIMED) {
            // Make user unable to cancel this activity if they were brought here by primed login
            cancelButton.setEnabled(false);

            // Show an explanatory message, since the user will have been brought here automatically
            // after logging in
            primedModeMessage.setVisibility(View.VISIBLE);

            // Clear the primed password
            userRecord.clearPrimedPassword();
            CommCareApplication._().getCurrentApp().getStorage(UserKeyRecord.class).write(userRecord);
        }

        setListeners();
        if (savedInstanceState != null && savedInstanceState.getBoolean(WAS_IN_CONFIRM_MODE)) {
            firstRoundPin = savedInstanceState.getString(FIRST_ROUND_PIN);
            setConfirmMode();
        } else {
            setInitialEntryMode();
        }
    }


    private void setListeners() {
        enterPinBox.addTextChangedListener(getPinTextWatcher(continueButton));

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
            Toast.makeText(this, Localization.get("pins.dont.match"), Toast.LENGTH_SHORT).show();
            setInitialEntryMode();
        }
    }

    private void setInitialEntryMode() {
        enterPinBox.setText("");
        enterPinBox.requestFocus();
        continueButton.setText(Localization.get("pin.continue.button"));
        if (userRecord.hasPinSet()) {
            promptText.setText(Localization.get("pin.directive.reset"));
        } else {
            promptText.setText(Localization.get("pin.directive.new"));
        }
        inConfirmMode = false;
    }

    private void setConfirmMode() {
        enterPinBox.setText("");
        enterPinBox.requestFocus();
        continueButton.setText(Localization.get("pin.confirm.button"));
        promptText.setText(Localization.get("pin.directive.confirm"));
        inConfirmMode = true;
    }

    private void assignPin(String pin) {
        userRecord.assignPinToRecord(pin, unhashedUserPassword);
        CommCareApplication._().getCurrentApp().getStorage(UserKeyRecord.class).write(userRecord);
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
            launchRememberPasswordConfirmDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    private void launchRememberPasswordConfirmDialog() {
        AlertDialogFactory factory = new AlertDialogFactory(this,
                Localization.get("remember.password.confirm.title"),
                Localization.get("remember.password.confirm.message"));

        factory.setPositiveButton(Localization.get("dialog.ok"), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                userRecord.setPrimedPassword(unhashedUserPassword);
                CommCareApplication._().getCurrentApp().getStorage(UserKeyRecord.class).write(userRecord);
                Intent i = new Intent();
                i.putExtra(CHOSE_REMEMBER_PASSWORD, true);
                setResult(RESULT_OK, i);
                finish();
            }
        });

        factory.setNegativeButton(Localization.get("option.cancel"), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        showAlertDialog(factory);
    }

    public static TextWatcher getPinTextWatcher(final Button confirmButton) {
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
                    confirmButton.setEnabled(true);
                } else {
                    confirmButton.setEnabled(false);
                }
            }
        };
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (inConfirmMode) {
            outState.putBoolean(WAS_IN_CONFIRM_MODE, true);
            outState.putString(FIRST_ROUND_PIN, firstRoundPin);
        }
    }

}

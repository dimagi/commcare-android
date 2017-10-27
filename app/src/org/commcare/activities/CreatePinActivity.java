package org.commcare.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.StandardAlertDialog;
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
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        userRecord = CommCareApplication.instance().getRecordForCurrentUser();
        unhashedUserPassword = CommCareApplication.instance().getSession().getLoggedInUser().getCachedPwd();
        LoginMode loginMode = (LoginMode)getIntent().getSerializableExtra(LoginActivity.LOGIN_MODE);

        if (loginMode == LoginMode.PRIMED) {
            // Make user unable to cancel this activity if they were brought here by primed login
            cancelButton.setEnabled(false);

            // Show an explanatory message, since the user will have been brought here automatically
            // after logging in
            primedModeMessage.setVisibility(View.VISIBLE);

            // Clear the primed password
            userRecord.clearPrimedPassword();
            CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class).write(userRecord);
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
        enterPinBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // processes the done/next keyboard action
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    if (pinLengthIsValid(enterPinBox.getText())) {
                        continueButton.performClick();
                    } else {
                        Toast.makeText(CreatePinActivity.this, Localization.get("pin.length.error"),
                                Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
                return false;
            }
        });

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
        enterPinBox.setImeOptions(EditorInfo.IME_ACTION_NEXT);
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

        // open up the keyboard if it was dismissed
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(enterPinBox, 0);

        setTextEntryKeyboardAction(enterPinBox, EditorInfo.IME_ACTION_DONE);

        continueButton.setText(Localization.get("pin.confirm.button"));
        promptText.setText(Localization.get("pin.directive.confirm"));
        inConfirmMode = true;
    }

    private static void setTextEntryKeyboardAction(EditText textEntry, int action) {
        // bug/feature that requires setting the input type to null then changing the action type
        int inputType = textEntry.getInputType();
        textEntry.setInputType(InputType.TYPE_NULL);
        textEntry.setImeOptions(action);
        textEntry.setInputType(inputType);
    }

    private void assignPin(String pin) {
        userRecord.assignPinToRecord(pin, unhashedUserPassword);
        CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class).write(userRecord);
        FirebaseAnalyticsUtil.reportFeatureUsage(AnalyticsParamValue.FEATURE_setPin);
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
        StandardAlertDialog d = new StandardAlertDialog(this,
                Localization.get("remember.password.confirm.title"),
                Localization.get("remember.password.confirm.message"));

        d.setPositiveButton(Localization.get("dialog.ok"), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissAlertDialog();
                userRecord.setPrimedPassword(unhashedUserPassword);
                CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class).write(userRecord);
                Intent i = new Intent();
                i.putExtra(CHOSE_REMEMBER_PASSWORD, true);
                setResult(RESULT_OK, i);
                finish();
            }
        });

        d.setNegativeButton(Localization.get("option.cancel"), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissAlertDialog();
            }
        });

        showAlertDialog(d);
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
                confirmButton.setEnabled(pinLengthIsValid(s));
            }
        };
    }

    public static boolean pinLengthIsValid(CharSequence s) {
        return s.length() == 4;
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

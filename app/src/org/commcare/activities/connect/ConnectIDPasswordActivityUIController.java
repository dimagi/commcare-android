package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_password)
public class ConnectIDPasswordActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_password_title, locale = "connect.password.title")
    private TextView titleTextView;
    @UiElement(value = R.id.connect_password_message, locale = "connect.password.message")
    private TextView messageTextView;
    @UiElement(value = R.id.connect_password_input, locale = "connect.password")
    private AutoCompleteTextView passwordInput;
    @UiElement(value = R.id.connect_password_repeat_input, locale = "connect.password.repeat")
    private AutoCompleteTextView passwordRepeatInput;

    @UiElement(value = R.id.connect_password_error_message)
    private TextView errorTextView;

    @UiElement(value = R.id.connect_password_button, locale = "connect.password.button")
    private Button button;

    protected final ConnectIDPasswordActivity activity;

    public ConnectIDPasswordActivityUIController(ConnectIDPasswordActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button.setOnClickListener(v -> activity.handleButtonPress());

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                activity.checkPasswords();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };

        passwordInput.addTextChangedListener(watcher);
        passwordRepeatInput.addTextChangedListener(watcher);
    }

    @Override
    public void refreshView() {

    }

    public void requestInputFocus() {
        ConnectIDKeyboardHelper.showKeyboardOnInput(activity, passwordInput);
    }

    public String getPasswordText() {
        return passwordInput.getText().toString();
    }

    public String getPasswordRepeatText() {
        return passwordRepeatInput.getText().toString();
    }

    public void setButtonEnabled(boolean enabled) {
        button.setEnabled(enabled);
    }

    public void setErrorText(String text) {
        errorTextView.setText(text);
    }
}

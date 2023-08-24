package org.commcare.activities.connect;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.utils.KeyboardHelper;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

/**
 * UI Controller, handles UI interaction with the owning Activity
 *
 * @author dviggiano
 */
@ManagedUi(R.layout.screen_connect_password)
public class ConnectIDPasswordActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_password_message)
    private TextView messageTextView;
    @UiElement(value = R.id.connect_password_input)
    private AutoCompleteTextView passwordInput;
    @UiElement(value = R.id.connect_password_repeat_input)
    private AutoCompleteTextView passwordRepeatInput;
    @UiElement(value = R.id.connect_password_error_message)
    private TextView errorTextView;
    @UiElement(value = R.id.connect_password_button)
    private Button button;

    protected final ConnectIDPasswordActivity activity;

    public ConnectIDPasswordActivityUIController(ConnectIDPasswordActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button.setOnClickListener(v -> activity.handleButtonPress());

        clearPasswordFields();

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
        KeyboardHelper.showKeyboardOnInput(activity, passwordInput);
    }

    public void clearPasswordFields() {
        passwordInput.setText("");
        passwordRepeatInput.setText("");
    }

    public void setMessageText(String text) {
        messageTextView.setText(text);
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

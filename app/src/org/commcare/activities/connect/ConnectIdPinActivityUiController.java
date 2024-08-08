package org.commcare.activities.connect;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
@ManagedUi(R.layout.screen_connect_pin)
public class ConnectIdPinActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_pin_title)
    private TextView titleTextView;
    @UiElement(value = R.id.connect_pin_message)
    private TextView messageTextView;
    @UiElement(value = R.id.connect_pin_input)
    private TextInputEditText pinInput;
    @UiElement( R.id.connect_pin_repeat_holder)
    private TextInputLayout pinRepeatHolder;
    @UiElement(value = R.id.connect_pin_repeat_input)
    private TextInputEditText pinRepeatInput;
    @UiElement(value = R.id.connect_pin_error_message)
    private TextView errorTextView;
    @UiElement(value = R.id.connect_pin_verify_forgot)
    private TextView forgotLink;
    @UiElement(value = R.id.connect_pin_button)
    private Button button;

    protected final ConnectIdPinActivity activity;

    public ConnectIdPinActivityUiController(ConnectIdPinActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button.setOnClickListener(v -> activity.handleButtonPress());
        forgotLink.setOnClickListener(v -> activity.handleForgotPress());

        clearPinFields();

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                activity.checkPin();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };

        pinInput.addTextChangedListener(watcher);
        pinRepeatInput.addTextChangedListener(watcher);
    }

    @Override
    public void refreshView() {
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(activity, pinInput);
    }

    public void clearPinFields() {
        pinInput.setText("");
        pinRepeatInput.setText("");
    }

    public void setTitleText(String text) {
        titleTextView.setText(text);
    }

    public void setMessageText(String text) {
        messageTextView.setText(text);
    }

    public void setPinRepeatTextVisible(boolean visible) {
        pinRepeatHolder.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setPinForgotTextVisible(boolean visible) {
        forgotLink.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setPinLength(int length) {
        InputFilter[] filter = new InputFilter[] { new InputFilter.LengthFilter(length)};
        pinInput.setFilters(filter);
        pinRepeatInput.setFilters(filter);
    }

    public void clearPin() {
        pinInput.setText("");
        pinRepeatInput.setText("");
    }
    public String getPinText() {
        return pinInput.getText().toString();
    }

    public String getPinRepeatText() {
        return pinRepeatInput.getText().toString();
    }

    public void setButtonEnabled(boolean enabled) {
        button.setEnabled(enabled);
    }

    public void setErrorText(String text) {
        errorTextView.setText(text);
    }
}


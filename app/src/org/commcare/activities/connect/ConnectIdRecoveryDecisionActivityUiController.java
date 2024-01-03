package org.commcare.activities.connect;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.RelativeLayout;
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
@ManagedUi(R.layout.screen_connect_recovery_decision)
public class ConnectIdRecoveryDecisionActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_recovery_message)
    private TextView messageTextView;

    @UiElement(R.id.connect_recovery_phone_block)
    private RelativeLayout phoneBlock;

    @UiElement(value = R.id.connect_recovery_phone_country_input)
    private AutoCompleteTextView countryCodeInput;
    @UiElement(value = R.id.connect_recovery_phone_input)
    private AutoCompleteTextView phoneInput;

    @UiElement(value = R.id.connect_recovery_phone_message)
    private TextView phoneMessageTextView;

    @UiElement(value = R.id.connect_recovery_button_1)
    private Button button1;

    @UiElement(value = R.id.connect_recovery_or)
    private TextView orText;

    @UiElement(value = R.id.connect_recovery_button_2)
    private Button button2;

    protected final ConnectIdRecoveryDecisionActivity activity;

    public ConnectIdRecoveryDecisionActivityUiController(ConnectIdRecoveryDecisionActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button1.setOnClickListener(v -> activity.handleButton1Press());
        button2.setOnClickListener(v -> activity.handleButton2Press());

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                activity.checkPhoneNumber();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        countryCodeInput.addTextChangedListener(watcher);
        phoneInput.addTextChangedListener(watcher);
    }

    @Override
    public void refreshView() {

    }

    public void setMessage(String message) {
        messageTextView.setText(message);
    }

    public void setPhoneInputVisible(boolean visible) {
        phoneBlock.setVisibility(visible ? View.VISIBLE : View.GONE);
        phoneMessageTextView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setCountryCode(String code) {
        countryCodeInput.setText(code);
    }

    public String getCountryCode() {
        return countryCodeInput.getText().toString();
    }

    public String getPhoneNumber() {
        return phoneInput.getText().toString();
    }

    public void setButton1Enabled(boolean enabled) {
        button1.setEnabled(enabled);
    }

    public void setPhoneMessage(String message) {
        phoneMessageTextView.setText(message);
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(activity, phoneInput);
    }

    public void setButton1Text(String text) {
        button1.setText(text);
    }

    public void setButton2Text(String text) {
        button2.setText(text);
    }

    public void setButton2Visible(boolean visible) {
        button2.setVisibility(visible ? View.VISIBLE : View.GONE);
        orText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

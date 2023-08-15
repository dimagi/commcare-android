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

@ManagedUi(R.layout.screen_connect_primary_phone)
public class ConnectIDPhoneActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_primary_phone_title)
    private TextView titleTextView;
    @UiElement(value = R.id.connect_primary_phone_message)
    private TextView messageTextView;
    @UiElement(value = R.id.connect_primary_phone_country_input)
    private AutoCompleteTextView countryCodeInput;
    @UiElement(value = R.id.connect_primary_phone_input)
    private AutoCompleteTextView phoneInput;
    @UiElement(value = R.id.connect_primary_phone_availability)
    private TextView availabilityTextView;

    @UiElement(value = R.id.connect_primary_phone_button)
    private Button button;


    protected final ConnectIDPhoneActivity activity;

    public ConnectIDPhoneActivityUIController(ConnectIDPhoneActivity activity) {
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

    public void setTitle(String title) {
        titleTextView.setText(title);
    }

    public void setMessage(String message) {
        messageTextView.setText(message);
    }

    public void setCountryCode(String code) {
        countryCodeInput.setText(code);
    }

    public String getCountryCode() {
        return countryCodeInput.getText().toString();
    }

    public void setPhoneNumber(String phone) {
        phoneInput.setText(phone);
    }

    public String getPhoneNumber() {
        return phoneInput.getText().toString();
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(activity, phoneInput);
    }


    public void setOkButtonEnabled(boolean enabled) {
        button.setEnabled(enabled);
    }

    public void setAvailabilityText(String text) {
        availabilityTextView.setText(text);
    }
}

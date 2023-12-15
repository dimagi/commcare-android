package org.commcare.activities.connect;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import com.hbb20.CountryCodePicker;

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
@ManagedUi(R.layout.screen_connect_primary_phone)
public class ConnectIdPhoneActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_primary_phone_title)
    private TextView titleTextView;
    @UiElement(value = R.id.connect_primary_phone_message)
    private TextView messageTextView;
    @UiElement(value = R.id.connect_primary_phone_country_picker)
    private CountryCodePicker countryCodeInput;
    @UiElement(value = R.id.connect_primary_phone_input)
    private AutoCompleteTextView phoneInput;
    @UiElement(value = R.id.connect_primary_phone_availability)
    private TextView availabilityTextView;

    @UiElement(value = R.id.connect_primary_phone_button)
    private Button button;


    protected final ConnectIdPhoneActivity activity;

    public ConnectIdPhoneActivityUiController(ConnectIdPhoneActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button.setOnClickListener(v -> activity.handleButtonPress());

        phoneInput.addTextChangedListener(new TextWatcher() {
            String lastValue = null;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(!s.toString().equals(lastValue)) {
                    lastValue = s.toString();
                    activity.checkPhoneNumber();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        countryCodeInput.registerCarrierNumberEditText(phoneInput);
        countryCodeInput.setOnCountryChangeListener(() -> activity.checkPhoneNumber());
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

    public boolean isPhoneValid() {
        return countryCodeInput.isValidFullNumber();
    }

    public void setPhoneNumber(String phone) {
        countryCodeInput.setFullNumber(phone);
    }

    public String getPhoneNumber() {
        return countryCodeInput.getFullNumberWithPlus();
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

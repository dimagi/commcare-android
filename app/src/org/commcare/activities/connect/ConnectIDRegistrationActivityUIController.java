package org.commcare.activities.connect;

import android.widget.AutoCompleteTextView;
import android.widget.Button;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_registration)
public class ConnectIDRegistrationActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_edit_id)
    private AutoCompleteTextView idInput;
    @UiElement(value = R.id.connect_edit_name)
    private AutoCompleteTextView nameInput;
    @UiElement(value = R.id.connect_alt_phone_country_input)
    private AutoCompleteTextView countryCodeInput;
    @UiElement(value = R.id.connect_alt_phone_input)
    private AutoCompleteTextView phoneInput;
    @UiElement(value = R.id.connect_register_button)
    private Button registerButton;

    protected final ConnectIDRegistrationActivity activity;

    public ConnectIDRegistrationActivityUIController(ConnectIDRegistrationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        registerButton.setOnClickListener(v -> activity.createAccount());
    }

    @Override
    public void refreshView() {

    }

    public void setUserId(String userId) {
        idInput.setText(userId);
    }

    public String getUserIdText() { return idInput.getText().toString(); }
    public String getNameText() { return nameInput.getText().toString(); }
    public void setAltCountryCode(String code) { countryCodeInput.setText(code); }
    public String getAltCountryCode() {
        return countryCodeInput.getText().toString();
    }
    public String getAltPhoneNumber() {
        return phoneInput.getText().toString();
    }
}

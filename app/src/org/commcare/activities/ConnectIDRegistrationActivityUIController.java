package org.commcare.activities;

import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_registration)
public class ConnectIDRegistrationActivityUIController implements CommCareActivityUIController {

    @UiElement(value = R.id.connect_register_title, locale = "connect.register.title")
    private TextView titleTextView;

    @UiElement(value = R.id.connect_label_id, locale = "connect.register.id")
    private TextView idLabelTextView;
    @UiElement(value = R.id.connect_edit_id)
    private AutoCompleteTextView idInput;

    @UiElement(value = R.id.connect_label_name, locale = "connect.register.name")
    private TextView nameLabelTextView;
    @UiElement(value = R.id.connect_edit_name)
    private AutoCompleteTextView nameInput;

    @UiElement(value = R.id.connect_label_dob, locale = "connect.register.dob")
    private TextView dobLabelTextView;
    @UiElement(value = R.id.connect_edit_dob)
    private AutoCompleteTextView dobInput;

    @UiElement(value = R.id.connect_label_phone, locale = "connect.register.phone")
    private TextView phoneLabelTextView;
    @UiElement(value = R.id.connect_edit_phone)
    private AutoCompleteTextView phoneInput;

    @UiElement(value = R.id.connect_label_alt_phone, locale = "connect.register.phone.alt")
    private TextView altPhoneLabelTextView;
    @UiElement(value = R.id.connect_edit_alt_phone)
    private AutoCompleteTextView altPhoneInput;

    @UiElement(value = R.id.connect_register_button, locale = "connect.register.create")
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
    public String getDOBText() { return dobInput.getText().toString(); }
    public String getPhoneText() { return phoneInput.getText().toString(); }
    public String getAltPhoneText() { return altPhoneInput.getText().toString(); }
}

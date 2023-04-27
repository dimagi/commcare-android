package org.commcare.activities;

import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_phone_verify)
public class ConnectIDPhoneVerificationActivityUIController implements CommCareActivityUIController {

    @UiElement(value = R.id.connect_phone_verify_title, locale = "connect.verify.phone.title")
    private TextView titleTextView;

    @UiElement(value = R.id.connect_phone_verify_label)
    private TextView labelTextView;
    @UiElement(value = R.id.connect_phone_verify_code)
    private AutoCompleteTextView codeInput;

    @UiElement(value = R.id.connect_phone_verify_resend, locale = "connect.verify.phone.resend")
    private TextView resendTextView;

    @UiElement(value = R.id.connect_phone_verify_button, locale = "connect.verify.phone.verify")
    private Button verifyButton;

    protected final ConnectIDPhoneVerificationActivity activity;

    public ConnectIDPhoneVerificationActivityUIController(ConnectIDPhoneVerificationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        resendTextView.setOnClickListener(arg0 -> activity.requestSMSCode());
        verifyButton.setOnClickListener(arg0 -> activity.verifySMSCode());
    }

    @Override
    public void refreshView() {

    }

    public void setLabelText(String text) {
        labelTextView.setText(text);
    }

    public String getCode() {
        return codeInput.getText().toString();
    }
}

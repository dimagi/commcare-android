package org.commcare.activities;

import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_login)
public class ConnectIDLoginActivityUIController implements CommCareActivityUIController {

    @UiElement(value = R.id.connect_login_title, locale = "connect.title")
    private TextView titleTextView;

    @UiElement(value = R.id.connect_login_message, locale = "connect.message")
    private TextView messageTextView;

    @UiElement(value = R.id.connect_fingerprint_button, locale = "connect.button.fingerprint")
    private Button fingerprintButton;

    @UiElement(value = R.id.connect_login_or, locale = "choice.or")
    private TextView orTextView;

    @UiElement(value = R.id.connect_pin_button, locale = "connect.button.pin")
    private Button pinButton;

    @UiElement(value = R.id.connect_trouble_link, locale = "connect.trouble.message")
    private TextView troubleTextView;

    protected final ConnectIDLoginActivity activity;

    public ConnectIDLoginActivityUIController(ConnectIDLoginActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        fingerprintButton.setOnClickListener(arg0 -> activity.performFingerprintUnlock());
        pinButton.setOnClickListener(arg0 -> activity.performPinUnlock());
        troubleTextView.setOnClickListener(arg0 -> activity.startAccountRecoveryWorkflow());
    }

    @Override
    public void refreshView() {
        //Nothing to do
    }

    public void setFingerprintEnabled(boolean enabled) {
        fingerprintButton.setEnabled(enabled);
    }

    public void setPinEnabled(boolean enabled) {
        pinButton.setEnabled(enabled);
    }
}

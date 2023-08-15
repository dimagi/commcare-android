package org.commcare.activities.connect;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_verify)
public class ConnectIDVerificationActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_verify_title)
    private TextView titleTextView;
    @UiElement(value = R.id.connect_verify_message)
    private TextView messageTextView;

    @UiElement(value = R.id.connect_verify_fingerprint_container)
    private LinearLayout fingerprintContainer;
    @UiElement(value = R.id.connect_verify_fingerprint_message)
    private TextView fingerprintTextView;
    @UiElement(value = R.id.connect_verify_fingerprint_icon)
    private ImageView fingerprintIcon;
    @UiElement(value = R.id.connect_verify_fingerprint_button)
    private Button fingerprintButton;

    @UiElement(value = R.id.connect_verify_or)
    private TextView orTextView;

    @UiElement(value = R.id.connect_verify_pin_container)
    private LinearLayout pinContainer;
    @UiElement(value = R.id.connect_verify_pin_message)
    private TextView pinTextView;
    @UiElement(value = R.id.connect_verify_pin_icon)
    private ImageView pinIcon;
    @UiElement(value = R.id.connect_verify_pin_button)
    private Button pinButton;

    @UiElement(value = R.id.connect_verify_password_link)
    private TextView passwordLink;

    private ConnectIDVerificationActivity activity;

    public ConnectIDVerificationActivityUIController(ConnectIDVerificationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        fingerprintButton.setOnClickListener(v -> activity.handleFingerprintButton());
        pinButton.setOnClickListener(v -> activity.handlePinButton());
        passwordLink.setOnClickListener(v -> activity.handlePasswordButton());
    }

    @Override
    public void refreshView() {

    }

    public void setTitleText(String text) {
        titleTextView.setText(text);
    }

    public void setMessageText(String text) {
        messageTextView.setText(text);
    }

    public void updateFingerprint(String fingerprintMessageText, String fingerprintButtonText) {
        boolean showFingerprint = fingerprintButtonText != null;
        fingerprintContainer.setVisibility(showFingerprint ? View.VISIBLE : View.GONE);
        if (showFingerprint) {
            fingerprintTextView.setText(fingerprintMessageText);
            fingerprintButton.setText(fingerprintButtonText);
        }
    }

    public void updatePin(String pinMessageText, String pinButtonText) {
        boolean showPin = pinButtonText != null;
        pinContainer.setVisibility(showPin ? View.VISIBLE : View.GONE);
        if (showPin) {
            pinTextView.setText(pinMessageText);
            pinButton.setText(pinButtonText);
        }
    }

    public void setOrVisibility(boolean visible) {
        orTextView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

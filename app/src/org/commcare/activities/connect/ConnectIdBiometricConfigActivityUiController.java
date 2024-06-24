package org.commcare.activities.connect;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

/**
 * UI Controller, handles UI interaction with the owning Activity
 *
 * @author dviggiano
 */
@ManagedUi(R.layout.screen_connect_verify)
public class ConnectIdBiometricConfigActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_verify_title)
    private TextView titleTextView;
    @UiElement(value = R.id.connect_verify_message)
    private TextView messageTextView;

    @UiElement(value = R.id.connect_verify_fingerprint_container)
    private LinearLayout fingerprintContainer;
    @UiElement(value = R.id.connect_verify_fingerprint_button)
    private Button fingerprintButton;

    @UiElement(value = R.id.connect_verify_or)
    private TextView orTextView;

    @UiElement(value = R.id.connect_verify_pin_container)
    private LinearLayout pinContainer;
    @UiElement(value = R.id.connect_verify_pin_button)
    private Button pinButton;

    private final ConnectIdBiometricConfigActivity activity;

    public ConnectIdBiometricConfigActivityUiController(ConnectIdBiometricConfigActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        fingerprintButton.setOnClickListener(v -> activity.handleFingerprintButton());
//        pinButton.setOnClickListener(v -> activity.handlePinButton());
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

    public void updateFingerprint(String fingerprintButtonText) {
        boolean showFingerprint = fingerprintButtonText != null;
        fingerprintContainer.setVisibility(showFingerprint ? View.VISIBLE : View.GONE);
        if (showFingerprint) {
            fingerprintButton.setText(fingerprintButtonText);
        }
    }

    public void updatePin(String pinButtonText) {
        boolean showPin = pinButtonText != null;
        pinContainer.setVisibility(showPin ? View.VISIBLE : View.GONE);
        if (showPin) {
            pinButton.setText(pinButtonText);
        }
    }

    public void setOrVisibility(boolean visible) {
        orTextView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

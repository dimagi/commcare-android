package org.commcare.activities;

import android.graphics.Color;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_verify)
public class ConnectIDVerificationActivityUIController implements CommCareActivityUIController {

    @UiElement(value = R.id.connect_verify_title, locale = "connect.verify.title")
    private TextView titleTextView;

    @UiElement(value = R.id.connect_verify_message, locale = "connect.verify.message")
    private TextView messageTextView;

    @UiElement(value = R.id.connect_verify_fingerprint_message, locale = "connect.verify.fingerprint")
    private TextView fingerprintTextView;

    @UiElement(value = R.id.connect_verify_pin_message, locale = "connect.verify.pin")
    private TextView pinTextView;

    @UiElement(value = R.id.connect_verify_button, locale = "connect.verify.button")
    private Button actionButton;


    private ConnectIDVerificationActivity activity;

    public ConnectIDVerificationActivityUIController(ConnectIDVerificationActivity activity) {
        this.activity = activity;
    }
    @Override
    public void setupUI() {
        fingerprintTextView.setOnClickListener(v -> activity.configureFingerprint());
        pinTextView.setOnClickListener(v -> activity.configurePin());
        actionButton.setOnClickListener(v -> activity.handleActionButton());
    }

    @Override
    public void refreshView() {

    }

    public void setFingerprintStatus(BiometricsHelper.ConfigurationStatus status) {
        setStatus(fingerprintTextView, status);
    }

    public void setPinStatus(BiometricsHelper.ConfigurationStatus status) {
        setStatus(pinTextView, status);
    }

    private void setStatus(TextView textView, BiometricsHelper.ConfigurationStatus status) {
        int color = Color.YELLOW;
        switch(status) {
            case NotAvailable -> color = Color.RED;
            case Configured -> color = Color.GREEN;
        }

        if(status == BiometricsHelper.ConfigurationStatus.Configured) {
            actionButton.setEnabled(true);
        }

        textView.setTextColor(color);
        textView.setEnabled(status != BiometricsHelper.ConfigurationStatus.NotAvailable);
    }
}

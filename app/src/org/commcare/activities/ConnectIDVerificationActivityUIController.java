package org.commcare.activities;

import android.widget.Button;
import android.widget.ImageView;
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

    @UiElement(value = R.id.connect_verify_fingerprint_icon)
    private ImageView fingerprintIcon;
    @UiElement(value = R.id.connect_verify_fingerprint_message, locale = "connect.verify.fingerprint")
    private TextView fingerprintTextView;

    @UiElement(value = R.id.connect_verify_pin_icon)
    private ImageView pinIcon;
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
        setStatus(fingerprintTextView, fingerprintIcon, status);
    }

    public void setPinStatus(BiometricsHelper.ConfigurationStatus status) {
        setStatus(pinTextView, pinIcon, status);
    }

    private void setStatus(TextView textView, ImageView iconView, BiometricsHelper.ConfigurationStatus status) {
        int image = R.drawable.redx;
        switch(status) {
            case NotAvailable -> {
                image = R.drawable.redx;
            }
            case NotConfigured -> {
                image = R.drawable.eye;
            }
            case Configured -> {
                image = R.drawable.checkmark;
            }
        }

        if(status == BiometricsHelper.ConfigurationStatus.Configured) {
            actionButton.setEnabled(true);
        }

        iconView.setImageResource(image);
        textView.setEnabled(status != BiometricsHelper.ConfigurationStatus.NotAvailable);
    }
}

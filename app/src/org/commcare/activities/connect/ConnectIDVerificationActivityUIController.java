package org.commcare.activities.connect;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.activities.BiometricsHelper;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_verify)
public class ConnectIDVerificationActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_verify_fingerprint_icon)
    private ImageView fingerprintIcon;
    @UiElement(value = R.id.connect_verify_fingerprint_message)
    private TextView fingerprintTextView;
    @UiElement(value = R.id.connect_verify_pin_icon)
    private ImageView pinIcon;
    @UiElement(value = R.id.connect_verify_pin_message)
    private TextView pinTextView;
    @UiElement(value = R.id.connect_verify_button)
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

    public void setButtonText(String text) {
        actionButton.setText(text);
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

package org.commcare.activities.connect;

import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_consent)
public class ConnectIDConsentActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_consent_title, locale = "connect.consent.title")
    private TextView titleTextView;
    @UiElement(value = R.id.connect_consent_message, locale = "connect.consent.message")
    private TextView messageTextView;
    @UiElement(value = R.id.connect_consent_button, locale = "connect.consent.button")
    private Button button;

    protected final ConnectIDConsentActivity activity;

    public ConnectIDConsentActivityUIController(ConnectIDConsentActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button.setOnClickListener(v -> activity.handleButtonPress());
    }

    @Override
    public void refreshView() {

    }
}

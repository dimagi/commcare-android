package org.commcare.activities.connect;

import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

/**
 * @author dviggiano
 * UI Controller, handles UI interaction with the owning Activity
 */
@ManagedUi(R.layout.screen_connect_message)
public class ConnectIDMessageActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_message_title)
    private TextView titleTextView;
    @UiElement(value = R.id.connect_message_message)
    private TextView messageTextView;
    @UiElement(value = R.id.connect_message_button)
    private Button button;

    protected final ConnectIDMessageActivity activity;

    public ConnectIDMessageActivityUIController(ConnectIDMessageActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button.setOnClickListener(v -> activity.handleButtonPress());
    }

    @Override
    public void refreshView() {

    }

    public void setTitle(String title) {
        titleTextView.setText(title);
    }

    public void setMessage(String message) {
        messageTextView.setText(message);
    }

    public void setButtonText(String buttonText) {
        button.setText(buttonText);
    }
}

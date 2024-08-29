package org.commcare.activities.connect;

import android.view.View;
import android.widget.Button;
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
@ManagedUi(R.layout.screen_connect_message)
public class ConnectIdMessageActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_message_title)
    private TextView titleTextView;
    @UiElement(value = R.id.connect_message_message)
    private TextView messageTextView;
    @UiElement(value = R.id.connect_message_button)
    private Button button;
    @UiElement(value = R.id.connect_message_button2)
    private Button button2;

    protected final ConnectIdMessageActivity activity;

    public ConnectIdMessageActivityUiController(ConnectIdMessageActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button.setOnClickListener(v -> activity.handleButtonPress(false));
        button2.setOnClickListener(v -> activity.handleButtonPress(true));
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

    public void setButton2Text(String buttonText) {
        boolean show = buttonText != null;
        button2.setVisibility(show ? View.VISIBLE : View.GONE);
        if(show) {
            button2.setText(buttonText);
        }
    }
}

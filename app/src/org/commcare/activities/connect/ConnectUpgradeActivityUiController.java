package org.commcare.activities.connect;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_upgrade)
public class ConnectUpgradeActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_upgrade_message)
    private TextView messageTextView;
    @UiElement(value = R.id.connect_upgrade_button)
    private Button button;

    private final ConnectUpgradeActivity activity;
    public ConnectUpgradeActivityUiController(ConnectUpgradeActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button.setOnClickListener(v -> activity.handleButtonPress());
    }

    @Override
    public void refreshView() {

    }

    public void setMessage(String message) { messageTextView.setText(message); }
    public void setButtonText(String text) { button.setText(text); }
    public void setButtonEnabled(boolean enabled) { button.setEnabled(enabled); }
}

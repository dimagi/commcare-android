package org.commcare.activities.connect;

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
@ManagedUi(R.layout.screen_connect_login)
public class ConnectIdLoginActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_pin_button)
    private Button pinButton;
    @UiElement(value = R.id.connect_password_button)
    private Button passwordButton;
    @UiElement(value = R.id.connect_trouble_link)
    private TextView troubleTextView;

    protected final ConnectIdLoginActivity activity;

    public ConnectIdLoginActivityUiController(ConnectIdLoginActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        passwordButton.setOnClickListener(arg0 -> activity.performPasswordUnlock());
        pinButton.setOnClickListener(arg0 -> activity.performPinUnlock());
        troubleTextView.setOnClickListener(arg0 -> activity.startAccountRecoveryWorkflow());
    }

    @Override
    public void refreshView() {
        //Nothing to do
    }
}

package org.commcare.activities.connect;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

/**
 * @author dviggiano
 */
@ManagedUi(R.layout.screen_connect_login)
public class ConnectIDLoginActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_pin_button)
    private Button pinButton;
    @UiElement(value = R.id.connect_login_or)
    private TextView orTextView;
    @UiElement(value = R.id.connect_password_button)
    private Button passwordButton;
    @UiElement(value = R.id.connect_trouble_link)
    private TextView troubleTextView;

    protected final ConnectIDLoginActivity activity;

    public ConnectIDLoginActivityUIController(ConnectIDLoginActivity activity) {
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

    public void showAdditionalOptions() {
        pinButton.setVisibility(View.VISIBLE);
        orTextView.setVisibility(View.VISIBLE);
        passwordButton.setVisibility(View.VISIBLE);
        troubleTextView.setVisibility(View.VISIBLE);
    }
}

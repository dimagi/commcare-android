package org.commcare.activities.connect;

import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.utils.KeyboardHelper;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

/**
 * UI Controller, handles UI interaction with the owning Activity
 *
 * @author dviggiano
 */
@ManagedUi(R.layout.screen_connect_password_verify)
public class ConnectIdPasswordVerificationActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_password_verify_input)
    private TextInputEditText passwordInput;
    @UiElement(value = R.id.connect_password_verify_forgot)
    private TextView forgotLink;
    @UiElement(value = R.id.connect_password_verify_button)
    private Button button;

    protected final ConnectIdPasswordVerificationActivity activity;

    public ConnectIdPasswordVerificationActivityUiController(ConnectIdPasswordVerificationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        forgotLink.setOnClickListener(arg0 -> activity.handleForgotPress());
        button.setOnClickListener(arg0 -> activity.handleButtonPress());

        clearPassword();
    }

    @Override
    public void refreshView() {

    }

    public String getPassword() {
        return passwordInput.getText().toString();
    }

    public void clearPassword() {
        passwordInput.setText("");
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(activity, passwordInput);
    }
}

package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_password_verify)
public class ConnectIDPasswordVerificationActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_password_verify_title, locale = "connect.password.verify.title")
    private TextView titleTextView;
    @UiElement(value = R.id.connect_password_verify_message, locale = "connect.password.verify.message")
    private TextView messageTextView;
    @UiElement(value = R.id.connect_password_verify_input, locale = "connect.password.verify")
    private AutoCompleteTextView passwordInput;

    @UiElement(value = R.id.connect_password_verify_forgot, locale = "connect.password.verify.forgot")
    private TextView forgotLink;

    @UiElement(value = R.id.connect_password_verify_button, locale = "connect.password.verify.button")
    private Button button;

    protected final ConnectIDPasswordVerificationActivity activity;

    public ConnectIDPasswordVerificationActivityUIController(ConnectIDPasswordVerificationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        forgotLink.setOnClickListener(arg0 -> activity.handleForgotPress());
        button.setOnClickListener(arg0 -> activity.handleButtonPress());
    }

    @Override
    public void refreshView() {

    }

    public String getPassword() {
        return passwordInput.getText().toString();
    }

    public void requestInputFocus() {
        ConnectIDKeyboardHelper.showKeyboardOnInput(activity, passwordInput);
    }
}

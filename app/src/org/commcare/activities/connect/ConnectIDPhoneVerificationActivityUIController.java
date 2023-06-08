package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_phone_verify)
public class ConnectIDPhoneVerificationActivityUIController implements CommCareActivityUIController {

    @UiElement(value = R.id.connect_phone_verify_title, locale = "connect.verify.phone.title")
    private TextView titleTextView;

    @UiElement(value = R.id.connect_phone_verify_label)
    private TextView labelTextView;
    @UiElement(value = R.id.connect_phone_verify_code)
    private AutoCompleteTextView codeInput;

    @UiElement(value = R.id.connect_phone_verify_change, locale = "connect.verify.phone.change")
    private TextView changeTextView;

    @UiElement(value = R.id.connect_phone_verify_resend, locale = "connect.verify.phone.resend")
    private TextView resendTextView;

    @UiElement(value = R.id.connect_phone_verify_button, locale = "connect.verify.phone.verify")
    private Button verifyButton;

    protected final ConnectIDPhoneVerificationActivity activity;

    public ConnectIDPhoneVerificationActivityUIController(ConnectIDPhoneVerificationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        resendTextView.setOnClickListener(arg0 -> activity.requestSMSCode());
        changeTextView.setOnClickListener(arg0 -> activity.changeNumber());
        verifyButton.setOnClickListener(arg0 -> activity.verifySMSCode());
    }

    @Override
    public void refreshView() {

    }

    public void setLabelText(String text) {
        labelTextView.setText(text);
    }

    public void showChangeOption() {
        changeTextView.setVisibility(View.VISIBLE);
    }

    public void requestInputFocus() {
        ConnectIDKeyboardHelper.showKeyboardOnInput(activity, codeInput);
    }

    public String getCode() {
        return codeInput.getText().toString();
    }
}

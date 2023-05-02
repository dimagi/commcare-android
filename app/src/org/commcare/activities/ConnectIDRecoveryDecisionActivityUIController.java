package org.commcare.activities;

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

@ManagedUi(R.layout.screen_connect_recovery_decision)
public class ConnectIDRecoveryDecisionActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_recovery_title, locale = "connect.recovery.title")
    private TextView titleTextView;
    @UiElement(value = R.id.connect_recovery_message)
    private TextView messageTextView;
    @UiElement(value = R.id.connect_recovery_phone)
    private AutoCompleteTextView phoneInput;
    @UiElement(value = R.id.connect_recovery_button_1)
    private Button button1;

    @UiElement(value = R.id.connect_recovery_or, locale = "choice.or")
    private TextView orTextView;
    @UiElement(value = R.id.connect_recovery_button_2)
    private Button button2;

    protected final ConnectIDRecoveryDecisionActivity activity;

    public ConnectIDRecoveryDecisionActivityUIController(ConnectIDRecoveryDecisionActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        button1.setOnClickListener(v -> activity.handleButton1Press());
        button2.setOnClickListener(v -> activity.handleButton2Press());
    }

    @Override
    public void refreshView() {

    }

    public void setMessage(String message) {
        messageTextView.setText(message);
    }

    public void setPhoneInputVisible(boolean visible) {
        phoneInput.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public String getPhoneNumber() {
        return phoneInput.getText().toString();
    }

    public void requestInputFocus(Activity activity) {
        phoneInput.requestFocus();

        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInput(phoneInput, InputMethodManager.SHOW_IMPLICIT);
    }

    public void setButton1Text(String text) {
        button1.setText(text);
    }

    public void setButton2Text(String text) {
        button2.setText(text);
    }

    public void setButton2Visible(boolean visible) {
        button2.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

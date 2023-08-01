package org.commcare.activities.connect;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.screen_connect_registration)
public class ConnectIDRegistrationActivityUIController implements CommCareActivityUIController {
    @UiElement(value = R.id.connect_edit_name)
    private AutoCompleteTextView nameInput;
    @UiElement(value = R.id.connect_registration_error)
    private TextView errorText;
    @UiElement(value = R.id.connect_register_button)
    private Button registerButton;

    protected final ConnectIDRegistrationActivity activity;

    public ConnectIDRegistrationActivityUIController(ConnectIDRegistrationActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        registerButton.setOnClickListener(v -> activity.continuePressed());

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                activity.updateStatus();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        };

        nameInput.addTextChangedListener(watcher);
    }

    @Override
    public void refreshView() {

    }


    public String getNameText() { return nameInput.getText().toString(); }
    public void setNameText(String name) { nameInput.setText(name); }
    public void setButtonEnabled(boolean enabled) { registerButton.setEnabled(enabled); }

    public void setErrorText(String text) {
        if(text == null) {
            errorText.setVisibility(View.GONE);
        }
        else {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(text);
        }
    }
}

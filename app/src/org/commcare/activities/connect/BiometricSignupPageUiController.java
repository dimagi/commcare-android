package org.commcare.activities.connect;

import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

@ManagedUi(R.layout.activity_biometric_signup_page)
public class BiometricSignupPageUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.switchBiometric)
    private Switch fingerprintButton;

    private final BiometricSignupPage activity;

    public BiometricSignupPageUiController(BiometricSignupPage activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        fingerprintButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    activity.handleFingerprintButton();
                }
            }
        });


    }

    @Override
    public void refreshView() {

    }
}

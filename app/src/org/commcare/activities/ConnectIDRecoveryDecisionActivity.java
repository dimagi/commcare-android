package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.javarosa.core.services.locale.Localization;

public class ConnectIDRecoveryDecisionActivity extends  CommCareActivity<ConnectIDRecoveryDecisionActivity>
implements WithUIController {
    private enum ConnectRecoveryState {
        NewOrRecover,
        PhoneOrExtended
    }

    public static final String CREATE = "CREATE";
    public static final String PHONE = "PHONE";
    private ConnectIDRecoveryDecisionActivityUIController uiController;
    private ConnectRecoveryState state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        state = ConnectRecoveryState.NewOrRecover;

        uiController.setupUI();

        uiController.setMessage(Localization.get("connect.recovery.decision.new"));
        uiController.setButton1Text(Localization.get("connect.recovery.button.new"));
        uiController.setButton2Text(Localization.get("connect.recovery.button.recover"));
    }

    @Override
    public void onResume() {
        super.onResume();

        if(state == ConnectRecoveryState.PhoneOrExtended) {
            uiController.requestInputFocus(this);
        }
    }

    @Override
    public CommCareActivityUIController getUIController() { return this.uiController; }

    @Override
    public void initUIController() {
        uiController = new ConnectIDRecoveryDecisionActivityUIController(this);
    }

    public void finish(boolean createNew, String phone) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(CREATE, createNew);
        intent.putExtra(PHONE, phone);

        setResult(RESULT_OK, intent);
        finish();
    }

    public void handleButton1Press() {
        switch(state) {
            case NewOrRecover -> finish(true, null);
            case PhoneOrExtended -> finish(false, uiController.getPhoneNumber());
        }
    }

    public void handleButton2Press() {
        switch(state) {
            case NewOrRecover -> {
                state = ConnectRecoveryState.PhoneOrExtended;
                uiController.setPhoneInputVisible(true);
                uiController.requestInputFocus(this);
                uiController.setMessage(Localization.get("connect.recovery.decision.phone"));
                uiController.setButton1Text(Localization.get("connect.recovery.button.phone"));
                uiController.setButton2Text(Localization.get("connect.recovery.button.extended"));
            }
            case PhoneOrExtended -> {
                Toast.makeText(this, "Not ready yet!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

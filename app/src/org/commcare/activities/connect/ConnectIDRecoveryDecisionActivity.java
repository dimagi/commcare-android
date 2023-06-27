package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.commcare.activities.CommCareActivity;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.utils.PhoneNumberHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class ConnectIDRecoveryDecisionActivity extends CommCareActivity<ConnectIDRecoveryDecisionActivity>
implements WithUIController {
    private enum ConnectRecoveryState {
        NewOrRecover,
        PhoneOrExtended
    }

    private ConnectIDRecoveryDecisionActivityUIController uiController;
    private ConnectRecoveryState state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_recovery_title));

        state = ConnectRecoveryState.NewOrRecover;

        uiController.setupUI();

        uiController.setMessage(getString(R.string.connect_recovery_decision_new));
        uiController.setButton1Text(getString(R.string.connect_recovery_button_new));
        uiController.setButton2Text(getString(R.string.connect_recovery_button_recover));
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        if(state == ConnectRecoveryState.PhoneOrExtended) {
            uiController.requestInputFocus();
            checkPhoneNumber();
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

        intent.putExtra(ConnectIDConstants.CREATE, createNew);
        intent.putExtra(ConnectIDConstants.PHONE, phone);

        setResult(RESULT_OK, intent);
        finish();
    }

    public void handleButton1Press() {
        switch(state) {
            case NewOrRecover -> finish(true, null);
            case PhoneOrExtended -> finish(false, PhoneNumberHelper.buildPhoneNumber(uiController.getCountryCode(), uiController.getPhoneNumber()));
        }
    }

    public void handleButton2Press() {
        switch(state) {
            case NewOrRecover -> {
                state = ConnectRecoveryState.PhoneOrExtended;
                uiController.setPhoneInputVisible(true);

                setTitle(getString(R.string.connect_recovery_title2));

                int code = PhoneNumberHelper.getCountryCode(this);
                uiController.setCountryCode(String.format(Locale.getDefault(), "+%d", code));

                uiController.requestInputFocus();
                uiController.setMessage(getString(R.string.connect_recovery_decision_phone));
                uiController.setButton1Text(getString(R.string.connect_recovery_button_phone));
                uiController.setButton2Visible(false);
            }
            case PhoneOrExtended -> {
                Toast.makeText(this, "Not ready yet!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void checkPhoneNumber() {
        String phone = PhoneNumberHelper.buildPhoneNumber(uiController.getCountryCode(), uiController.getPhoneNumber());

        boolean valid = PhoneNumberHelper.isValidPhoneNumber(this, phone);

        if (valid) {
            phone = phone.replaceAll("\\+", "%2b");
            uiController.setPhoneMessage(getString(R.string.connect_phone_checking));
            uiController.setButton1Enabled(false);

            Multimap<String, String> params = ArrayListMultimap.create();
            params.put("phone_number", phone);

            String url = this.getString(R.string.ConnectURL) + "/users/phone_available";

            ConnectIDNetworkHelper.get(this, url, new AuthInfo.NoAuth(), params, new ConnectIDNetworkHelper.INetworkResultHandler() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    uiController.setPhoneMessage(getString(R.string.connect_phone_not_found));
                    uiController.setButton1Enabled(false);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    uiController.setPhoneMessage("");
                    uiController.setButton1Enabled(true);
                }
            });
        } else {
            uiController.setPhoneMessage(getString(R.string.connect_phone_invalid));
            uiController.setButton1Enabled(false);
        }
    }
}

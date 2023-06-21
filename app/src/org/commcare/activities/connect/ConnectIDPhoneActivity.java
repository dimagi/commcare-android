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
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertPathValidatorException;
import java.util.HashMap;
import java.util.Locale;

public class ConnectIDPhoneActivity extends CommCareActivity<ConnectIDPhoneActivity>
implements WithUIController {

    private boolean requireUnusedNumber = false;
    private String existingPhone;
    private String username;
    private String password;
    private ConnectIDPhoneActivityUIController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();

        requireUnusedNumber = getIntent().getStringExtra(ConnectIDConstants.METHOD).equals("true");
        existingPhone = getIntent().getStringExtra(ConnectIDConstants.PHONE);
        username = getIntent().getStringExtra(ConnectIDConstants.USERNAME);
        password = getIntent().getStringExtra(ConnectIDConstants.PASSWORD);

        int code = PhoneNumberHelper.getCountryCode(this);
        uiController.setCountryCode(String.format(Locale.getDefault(), "+%d", code));
    }

    @Override
    public void onResume() {
        super.onResume();

        checkPhoneNumber();

        uiController.requestInputFocus();
    }

    @Override
    public CommCareActivityUIController getUIController() { return this.uiController; }

    @Override
    public void initUIController() {
        uiController = new ConnectIDPhoneActivityUIController(this);
    }

    public void finish(boolean success, String phone) {
        Intent intent = new Intent(getIntent());

        intent.putExtra(ConnectIDConstants.PHONE, phone);

        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    public void handleButtonPress() {
        String phone = PhoneNumberHelper.buildPhoneNumber(uiController.getCountryCode(), uiController.getPhoneNumber());
        if(existingPhone != null && !existingPhone.equals(phone)) {
            //Update the phone number with the server
            HashMap<String, String> params = new HashMap<>();
            params.put("old_phone_number", existingPhone);
            params.put("new_phone_number", phone);
            String url = getString(R.string.ConnectURL) + "/users/change_phone";

            ConnectIDNetworkHelper.post(this, url, new AuthInfo.ProvidedAuth(username, password, false), params, new ConnectIDNetworkHelper.INetworkResultHandler() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    finish(true, phone);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    Toast.makeText(getApplicationContext(), "Phone change error", Toast.LENGTH_SHORT).show();
                }
            });
        }
        else {
            finish(true, phone);
        }
    }

    public void checkPhoneNumber() {
        String phone = PhoneNumberHelper.buildPhoneNumber(uiController.getCountryCode(), uiController.getPhoneNumber());

        boolean valid = PhoneNumberHelper.isValidPhoneNumber(this, phone);

        if (valid) {
            if(requireUnusedNumber) {
                if(existingPhone != null && existingPhone.equals(phone)) {
                    uiController.setAvailabilityText("");
                    uiController.setOkButtonEnabled(true);
                }
                else {
                    phone = phone.replaceAll("\\+", "%2b");
                    uiController.setAvailabilityText(getString(R.string.connect_phone_checking));
                    uiController.setOkButtonEnabled(false);

                    Multimap<String, String> params = ArrayListMultimap.create();
                    params.put("phone_number", phone);

                    String url = this.getString(R.string.ConnectURL) + "/users/phone_available";

                    Context context = this;
                    final String phoneLock = phone;
                    ConnectIDNetworkHelper.get(this, url, new AuthInfo.NoAuth(), params, new ConnectIDNetworkHelper.INetworkResultHandler() {
                        @Override
                        public void processSuccess(int responseCode, InputStream responseData) {
                            uiController.setAvailabilityText(getString(R.string.connect_phone_available));
                            uiController.setOkButtonEnabled(true);
                        }

                        @Override
                        public void processFailure(int responseCode, IOException e) {
                            String text = getString(R.string.connect_phone_unavailable);
                            uiController.setOkButtonEnabled(false);

                            if(e != null) {
                                Logger.exception("Checking phone number", e);
                            }

                            uiController.setAvailabilityText(text);
                        }
                    });
                }
            }
            else {
                uiController.setAvailabilityText("");
                uiController.setOkButtonEnabled(true);
            }
        } else {
            uiController.setAvailabilityText(getString(R.string.connect_phone_invalid));
            uiController.setOkButtonEnabled(false);
        }
    }
}

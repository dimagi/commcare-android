package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
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
    private String altPhone;
    private String username;
    private String password;
    private ConnectIDPhoneActivityUIController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.connect_phone_page_title));

        uiController.setupUI();

        requireUnusedNumber = getIntent().getStringExtra(ConnectIDConstants.METHOD).equals("true");
        existingPhone = getIntent().getStringExtra(ConnectIDConstants.PHONE);
        altPhone = getIntent().getStringExtra(ConnectIDConstants.ALT_PHONE);
        username = getIntent().getStringExtra(ConnectIDConstants.USERNAME);
        password = getIntent().getStringExtra(ConnectIDConstants.PASSWORD);

        int code = PhoneNumberHelper.getCountryCode(this);
        String codeText = String.format(Locale.getDefault(), "+%d", code);

        ConnectUserRecord user = ConnectIDManager.getUser(this);
        if(user != null) {
            username = user.getUserID();
            password = user.getPassword();

            String phone = user.getPrimaryPhone();
            int existingCode = PhoneNumberHelper.getCountryCode(this, phone);
            if(existingCode > 0) {
                code = existingCode;
                codeText = String.format(Locale.getDefault(), "+%d", code);

                phone = phone.substring(codeText.length());
            }

            uiController.setPhoneNumber(phone);
        }

        uiController.setCountryCode(codeText);
    }

    @Override
    public void onResume() {
        super.onResume();

        checkPhoneNumber();

        uiController.requestInputFocus();
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
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
        ConnectUserRecord user = ConnectIDManager.getUser(this);
        String existing = user != null ? user.getPrimaryPhone() : existingPhone;
        if(existing != null && !existing.equals(phone)) {
            //Update the phone number with the server
            HashMap<String, String> params = new HashMap<>();
            params.put("old_phone_number", existing);
            params.put("new_phone_number", phone);
            String url = getString(R.string.ConnectURL) + "/users/change_phone";

            ConnectIDNetworkHelper.post(this, url, new AuthInfo.ProvidedAuth(username, password, false), params, false, new ConnectIDNetworkHelper.INetworkResultHandler() {
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
        ConnectUserRecord user = ConnectIDManager.getUser(this);

        if (valid) {
            if(requireUnusedNumber) {
                String existing = user != null ? user.getPrimaryPhone() : existingPhone;
                if(existing != null && existing.equals(phone)) {
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
            else if(altPhone != null && altPhone.equals(phone)) {
                uiController.setAvailabilityText(getString(R.string.connect_phone_not_alt));
                uiController.setOkButtonEnabled(false);
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
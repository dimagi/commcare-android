package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;

import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.interfaces.WithUIController;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTaskConnector;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Random;

import okhttp3.MediaType;
import okhttp3.RequestBody;

public class ConnectIDRegistrationActivity extends CommCareActivity<ConnectIDRegistrationActivity>
implements WithUIController, ConnectorWithHttpResponseProcessor<ConnectIDRegistrationActivity> {
    private static final String TAG = ConnectIDRegistrationActivity.class.getSimpleName();

    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String NAME = "NAME";
    public static final String DOB = "DOB";
    public static final String PHONE = "PHONE";
    public static final String ALTPHONE = "ALTPHONE";

    private ConnectIDRegistrationActivityUIController uiController;

    private ConnectIDUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();

        uiController.setUserId(generateUserId());
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void initUIController() {
        uiController = new ConnectIDRegistrationActivityUIController(this);
    }

    private String generateUserId() {
        int idLength = 7;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String userId = "";
        for (int i = 0; i < idLength; i++) {
            userId += charSet.charAt(new Random().nextInt(charSet.length()));
        }

        return userId;
    }

    private String generatePassword() {
        int passwordLength = 10;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+,<.>?;:[{]}|~";
        String password = "";
        for (int i = 0; i < passwordLength; i++) {
            password += charSet.charAt(new Random().nextInt(charSet.length()));
        }

        return password;
    }

    public void createAccount() {
        String url = getString(R.string.ConnectURL) + "/users/register";

        user = new ConnectIDUser();
        user.Username = uiController.getUserIdText();
        user.Name = uiController.getNameText();
        user.DOB = uiController.getDOBText();
        user.Phone = uiController.getPhoneText();
        user.AltPhone = uiController.getAltPhoneText();
        user.Password = generatePassword();

        HashMap<String, String> params = new HashMap<>();
        //params.put("device_id", CommCareApplication.instance().getPhoneId());
        params.put("username", user.Username);
        params.put("password", user.Password);
        params.put("name", user.Name);
        params.put("dob", user.DOB);
        params.put("phone_number", user.Phone);
        //params.put("recovery_phone", user.AltPhone);

        Gson gson = new Gson();
        String json = gson.toJson(params);

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), json);
        //RequestBody requestBody = ModernHttpRequester.getPostBody(params);
        ModernHttpTask postTask =
                new ModernHttpTask(this, url,
                        ImmutableMultimap.of(),
                        new HashMap<>(),
                        requestBody,
                        HTTPMethod.POST,
                        new AuthInfo.NoAuth());
        postTask.connect((CommCareTaskConnector)this);
        postTask.executeParallel();
    }

    public void finish(boolean success) {
        Intent intent = new Intent(getIntent());
        intent.putExtra(USERNAME, user.Username);
        intent.putExtra(PASSWORD, user.Password);
        intent.putExtra(NAME, user.Name);
        intent.putExtra(DOB, user.DOB);
        intent.putExtra(PHONE, user.Phone);
        intent.putExtra(ALTPHONE, user.AltPhone);
        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    @Override
    public void processSuccess(int responseCode, InputStream responseData) {
        Toast.makeText(this, "Success!", Toast.LENGTH_SHORT).show();
        finish(true);
    }

    @Override
    public void processClientError(int responseCode) {
        //400 error
        Toast.makeText(this, "Client error", Toast.LENGTH_SHORT).show();
        //finish(false);
    }

    @Override
    public void processServerError(int responseCode) {
        Toast.makeText(this, "Server error", Toast.LENGTH_SHORT).show();
        //500 error for internal server error
        //finish(false);
    }

    @Override
    public void processOther(int responseCode) {
        finish(false);
    }

    @Override
    public void handleIOException(IOException exception) {
        Toast.makeText(this, "Exception", Toast.LENGTH_SHORT).show();
        //UnknownHostException if host not found
        //finish(false);
    }
}

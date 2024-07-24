package org.commcare.activities.connectId.fragments;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Locale;
import java.util.Random;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link RegistrationFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RegistrationFragment extends Fragment {
    private AutoCompleteTextView nameInput;
    private TextView errorText;
    private Button registerButton;
    private ConnectUserRecord user;
    private String phone;

    TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
           updateStatus();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    public RegistrationFragment() {
        // Required empty public constructor
    }

    public static RegistrationFragment newInstance(String param1, String param2) {
        RegistrationFragment fragment = new RegistrationFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view= inflater.inflate(R.layout.screen_connect_registration, container, false);
        nameInput= view.findViewById(R.id.connect_edit_name);
        errorText= view.findViewById(R.id.connect_registration_error);
        registerButton= view.findViewById(R.id.connect_register_button);
        registerButton.setOnClickListener(v -> continuePressed());
        nameInput.addTextChangedListener(watcher);
        getActivity().setTitle(getString(R.string.connect_register_title));

        phone = getActivity().getIntent().getStringExtra(ConnectConstants.PHONE);
        ConnectUserRecord user = ConnectManager.getUser(getActivity());
        if (user != null) {
            nameInput.setText(user.getName());
        }

        updateStatus();

        return  view;
    }

    public void setErrorText(String text) {
        if (text == null) {
            errorText.setVisibility(View.GONE);
        } else {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(text);
        }
    }


    private String generateUserId() {
        int idLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder userId = new StringBuilder();
        for (int i = 0; i < idLength; i++) {
            userId.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return userId.toString();
    }

    public void updateStatus() {
        String error = nameInput.getText().length() == 0 ?
                getString(R.string.connect_register_error_name) : null;

        errorText.setText(error);
        registerButton.setEnabled(error == null);
    }

    public void finish(boolean success) {
//        Intent intent = new Intent(getIntent());
//        user.putUserInIntent(intent);
//        setResult(success ? RESULT_OK : RESULT_CANCELED, intent);
//        finish();
    }

    public void continuePressed() {
        user = ConnectManager.getUser(getActivity());
        if (user == null) {
            createAccount();
        } else {
            updateAccount();
        }
    }

    public void createAccount() {
        errorText.setText(null);

        ConnectUserRecord tempUser = new ConnectUserRecord(phone, generateUserId(), ConnectManager.generatePassword(),
                nameInput.getText().toString(), "");

        final Context context = getActivity();
        boolean isBusy = !ApiConnectId.registerUser(requireActivity(), tempUser.getUserId(), tempUser.getPassword(),
                tempUser.getName(), phone, new IApiCallback() {
                    @Override
                    public void processSuccess(int responseCode, InputStream responseData) {
                        user = tempUser;
                        try {
                            String responseAsString = new String(
                                    StreamsUtil.inputStreamToByteArray(responseData));
                            JSONObject json = new JSONObject(responseAsString);
                            String key = ConnectConstants.CONNECT_KEY_DB_KEY;
                            if (json.has(key)) {
                                ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(key));
                            }

                            key = ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY;
                            user.setSecondaryPhoneVerified(!json.has(key) || json.isNull(key));
                            if (!user.getSecondaryPhoneVerified()) {
                                user.setSecondaryPhoneVerifyByDate(ConnectNetworkHelper.parseDate(json.getString(key)));
                            }

                            ConnectDatabaseHelper.storeUser(context, user);
                        } catch (IOException | JSONException | ParseException e) {
                            Logger.exception("Parsing return from confirm_secondary_otp", e);
                        }

                        finish(true);
                    }

                    @Override
                    public void processFailure(int responseCode, IOException e) {
                        errorText.setText(String.format(Locale.getDefault(), "Registration error: %d",
                                responseCode));
                    }

                    @Override
                    public void processNetworkFailure() {
                        errorText.setText(getString(R.string.recovery_network_unavailable));
                    }

                    @Override
                    public void processOldApiError() {
                        errorText.setText(getString(R.string.recovery_network_outdated));
                    }
                });

        if (isBusy) {
            Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void updateAccount() {
        errorText.setText(null);

        String newName = nameInput.getText().toString();

        if (newName.equals(user.getName())) {
            finish(true);
        } else {
            boolean isBusy = !ApiConnectId.updateUserProfile(requireActivity(), user.getUserId(),
                    user.getPassword(), newName, null, new IApiCallback() {
                        @Override
                        public void processSuccess(int responseCode, InputStream responseData) {
                            user.setName(newName);
                            finish(true);
                        }

                        @Override
                        public void processFailure(int responseCode, IOException e) {
                            errorText.setText(String.format(Locale.getDefault(), "Error: %d",
                                    responseCode));
                        }

                        @Override
                        public void processNetworkFailure() {
                            errorText.setText(getString(R.string.recovery_network_unavailable));
                        }

                        @Override
                        public void processOldApiError() {
                            errorText.setText(getString(R.string.recovery_network_outdated));
                        }
                    });

            if (isBusy) {
                Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
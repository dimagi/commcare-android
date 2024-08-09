package org.commcare.fragments.connectId;

import android.content.Context;
import android.os.Bundle;
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
import org.commcare.dalvik.databinding.ScreenConnectRecoveryDecisionBinding;
import org.commcare.dalvik.databinding.ScreenConnectRegistrationBinding;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Locale;
import java.util.Random;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import static org.commcare.connect.ConnectTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdRegistrationFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectIdRegistrationFragment extends Fragment {
    private ConnectUserRecord user;
    private String phone;
    
    private ScreenConnectRegistrationBinding binding;

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

    public ConnectIdRegistrationFragment() {
        // Required empty public constructor
    }

    public static ConnectIdRegistrationFragment newInstance(String param1, String param2) {
        ConnectIdRegistrationFragment fragment = new ConnectIdRegistrationFragment();
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
        binding= ScreenConnectRegistrationBinding.inflate(inflater,container,false);
        View view= binding.getRoot();
        binding.connectRegisterButton.setOnClickListener(v -> continuePressed());
        binding.connectEditName.addTextChangedListener(watcher);
        getActivity().setTitle(getString(R.string.connect_register_title));

        if (getArguments() != null) {
            phone = ConnectIdRegistrationFragmentArgs.fromBundle(getArguments()).getPhone();
        }
        ConnectUserRecord user = ConnectManager.getUser(getActivity());
        if (user != null) {
            binding.connectEditName.setText(user.getName());
        }

        updateStatus();

        return  view;
    }

    public void setErrorText(String text) {
        if (text == null) {
            binding.connectRegistrationError.setVisibility(View.GONE);
        } else {
            binding.connectRegistrationError.setVisibility(View.VISIBLE);
            binding.connectRegistrationError.setText(text);
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
        String error = binding.connectEditName.getText().length() == 0 ?
                getString(R.string.connect_register_error_name) : null;

        binding.connectRegistrationError.setText(error);
        binding.connectRegisterButton.setEnabled(error == null);
    }

    public void finish(boolean success) {
        NavDirections directions;
        if (success) {
            ConnectUserRecord dbUser = ConnectDatabaseHelper.getUser(getActivity());
            if (dbUser != null) {
                dbUser.setName(user.getName());
                dbUser.setAlternatePhone(user.getAlternatePhone());
                user = dbUser;
            } else {
                ConnectManager.connectStatus = ConnectManager.ConnectIdStatus.Registering;
            }
            ConnectDatabaseHelper.storeUser(getActivity(), user);
            ConnectDatabaseHelper.setRegistrationPhase(getActivity(), CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
            directions = ConnectIdRegistrationFragmentDirections.actionConnectidRegistrationToConnectidBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
        } else {
            directions = ConnectIdRegistrationFragmentDirections.actionConnectidRegistrationToConnectidPhone(ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE,ConnectConstants.METHOD_REGISTER_PRIMARY,user.getPrimaryPhone());
        }
        Navigation.findNavController(binding.connectRegisterButton).navigate(directions);
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
        binding.connectRegistrationError.setText(null);

        ConnectUserRecord tempUser = new ConnectUserRecord(phone, generateUserId(), ConnectManager.generatePassword(),
                binding.connectEditName.getText().toString(), "");

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
                        setErrorText(String.format(Locale.getDefault(), "Registration error: %d",
                                responseCode));
                    }

                    @Override
                    public void processNetworkFailure() {
                        setErrorText(getString(R.string.recovery_network_unavailable));
                    }

                    @Override
                    public void processOldApiError() {
                        setErrorText(getString(R.string.recovery_network_outdated));
                    }
                });

        if (isBusy) {
            Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void updateAccount() {
        binding.connectRegistrationError.setText(null);

        String newName = binding.connectEditName.getText().toString();

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
                            binding.connectRegistrationError.setText(String.format(Locale.getDefault(), "Error: %d",
                                    responseCode));
                        }

                        @Override
                        public void processNetworkFailure() {
                            binding.connectRegistrationError.setText(getString(R.string.recovery_network_unavailable));
                        }

                        @Override
                        public void processOldApiError() {
                            binding.connectRegistrationError.setText(getString(R.string.recovery_network_outdated));
                        }
                    });

            if (isBusy) {
                Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
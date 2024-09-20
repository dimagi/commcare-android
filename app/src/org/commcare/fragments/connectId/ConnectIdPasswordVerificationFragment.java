package org.commcare.fragments.connectId;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPasswordVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.KeyboardHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class ConnectIdPasswordVerificationFragment extends Fragment {
    private int callingClass;
    public static final int PASSWORD_FAIL = 1;
    public static final int PASSWORD_LOCK = 2;
    private String phone = null;
    private String secretKey = null;
    private static final int MaxFailures = 3;
    private int failureCount = 0;

    private ScreenConnectPasswordVerifyBinding binding;

    public ConnectIdPasswordVerificationFragment() {
        // Required empty public constructor
    }

    public static ConnectIdPasswordVerificationFragment newInstance() {
        ConnectIdPasswordVerificationFragment fragment = new ConnectIdPasswordVerificationFragment();
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
        binding = ScreenConnectPasswordVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        phone = ConnectIdPasswordVerificationFragmentArgs.fromBundle(getArguments()).getPhone();
        secretKey = ConnectIdPasswordVerificationFragmentArgs.fromBundle(getArguments()).getSecret();
        callingClass = ConnectIdPasswordVerificationFragmentArgs.fromBundle(getArguments()).getCallingClass();
        failureCount = 0;
        binding.connectPasswordVerifyForgot.setOnClickListener(arg0 -> handleForgotPress());
        binding.connectPasswordVerifyButton.setOnClickListener(arg0 -> handleButtonPress());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        requestInputFocus();
    }

    public void finish(boolean success, boolean forgot) {
        NavDirections directions = null;
        switch (callingClass) {
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD:
                if (success) {
                    directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidPin(ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret).setChange(true).setRecover(true);
                    if (forgot) {
                        directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE, getString(R.string.connect_recovery_alt_button), null);
                    }
                } else {
                    directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragmnet.MethodRecoveryPrimary), ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverPhone, "", null).setAllowChange(false);
                }
                break;
            case ConnectConstants.CONNECT_UNLOCK_PASSWORD:
                if (success) {
                    if (forgot) {
                        ConnectIdActivity.forgotPassword = true;
                        directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidPhoneNo(ConnectConstants.METHOD_RECOVER_PRIMARY, null, ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE);
                    } else {
                        ConnectIdActivity.forgotPassword = false;
                        FirebaseAnalyticsUtil.reportCccSignIn(AnalyticsParamValue.CCC_SIGN_IN_METHOD_PASSWORD);
                        ConnectUserRecord user = ConnectDatabaseHelper.getUser(requireActivity());
                        user.setLastPinDate(new Date());
                        ConnectDatabaseHelper.storeUser(requireActivity(), user);
                        if (user.shouldRequireSecondaryPhoneVerification()) {
                            directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button));
                        } else {
                            ConnectManager.setStatus(ConnectManager.ConnectIdStatus.LoggedIn);
                            ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                            requireActivity().setResult(RESULT_OK);
                            requireActivity().finish();
                        }
                    }
                }
                break;
        }
        if (directions != null) {
            Navigation.findNavController(binding.connectPasswordVerifyButton).navigate(directions);
        }
    }

    public void handleWrongPassword() {
        failureCount++;
        logRecoveryResult(false);
        binding.connectPasswordVerifyInput.setText("");

        int requestCode = PASSWORD_FAIL;
        int message = R.string.connect_password_fail_message;

        if (failureCount >= MaxFailures) {
            requestCode = PASSWORD_LOCK;
            message = R.string.connect_password_recovery_message;
        }
        NavDirections directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidMessage(getString(R.string.connect_password_fail_title), getString(message), ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD, getString(R.string.connect_recovery_success_button), null);

        Navigation.findNavController(binding.connectPasswordVerifyButton).navigate(directions);

    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PASSWORD);
    }

    public void handleForgotPress() {
        finish(true, true);
    }

    public void handleButtonPress() {
        String password = Objects.requireNonNull(binding.connectPasswordVerifyInput.getText()).toString();
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(requireActivity());
        if (user != null) {
            //If we have the password stored locally, no need for network call
            if (password.equals(user.getPassword())) {
                logRecoveryResult(true);
                finish(true, false);
            } else {
                handleWrongPassword();
            }
        } else {
            final Context context = requireActivity();
            boolean isBusy = !ApiConnectId.checkPassword(requireActivity(), phone, secretKey, password, new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    String username = null;
                    String name = null;
                    try {
                        String responseAsString = new String(
                                StreamsUtil.inputStreamToByteArray(responseData));
                        if (responseAsString.length() > 0) {
                            JSONObject json = new JSONObject(responseAsString);
                            String key = ConnectConstants.CONNECT_KEY_USERNAME;
                            if (json.has(key)) {
                                username = json.getString(key);
                            }

                            key = ConnectConstants.CONNECT_KEY_NAME;
                            if (json.has(key)) {
                                name = json.getString(key);
                            }

                            key = ConnectConstants.CONNECT_KEY_DB_KEY;
                            if (json.has(key)) {
                                ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(key));
                            }

                            ConnectUserRecord user = new ConnectUserRecord(phone, username,
                                    password, name, "");

                            key = ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY;
                            user.setSecondaryPhoneVerified(!json.has(key) || json.isNull(key));
                            if (!user.getSecondaryPhoneVerified()) {
                                user.setSecondaryPhoneVerifyByDate(ConnectNetworkHelper.parseDate(json.getString(key)));
                            }

                            //TODO: Need to get secondary phone from server
                            ConnectDatabaseHelper.storeUser(context, user);
                        }
                    } catch (IOException | JSONException | ParseException e) {
                        Logger.exception("Parsing return from OTP request", e);
                    }

                    logRecoveryResult(true);
                    finish(true, false);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    handleWrongPassword();
                }

                @Override
                public void processNetworkFailure() {
                    ConnectNetworkHelper.showOutdatedApiError(requireActivity().getApplicationContext());
                }

                @Override
                public void processOldApiError() {
                    ConnectNetworkHelper.showOutdatedApiError(requireActivity().getApplicationContext());
                }
            });

            if (isBusy) {
                Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectPasswordVerifyInput);
    }

}
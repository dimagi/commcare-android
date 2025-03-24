package org.commcare.fragments.connectId;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.checkerframework.checker.units.qual.A;
import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPasswordVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.KeyboardHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import static android.app.Activity.RESULT_OK;

public class ConnectIdPasswordVerificationFragment extends Fragment {
    private int callingClass;
    public static final int PASSWORD_FAIL = 1;
    public static final int PASSWORD_LOCK = 2;
    private String phone = null;
    private String secretKey = null;
    private static final int MaxFailures = 3;
    private int failureCount = 0;
    // Added keys for state restoration
    private static final String KEY_FAILURE_COUNT = "failure_count";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_SECRET_KEY = "secret_key";
    private ScreenConnectPasswordVerifyBinding binding;
    private Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSavedState(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = ScreenConnectPasswordVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        failureCount = 0;
        setArguments();
        activity=requireActivity();
        binding.connectPasswordVerifyForgot.setOnClickListener(arg0 -> onForgotPasswordClick());
        binding.connectPasswordVerifyButton.setOnClickListener(arg0 -> onVerifyPasswordClick());
        activity.setTitle(R.string.connect_appbar_title_password_verification);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        requestInputFocus();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_FAILURE_COUNT, failureCount);
        outState.putString(KEY_PHONE, phone);
        outState.putString(KEY_SECRET_KEY, secretKey);
    }

    private void getSavedState(Bundle savedInstanceState){
        if (savedInstanceState != null) {
            failureCount = savedInstanceState.getInt(KEY_FAILURE_COUNT);
            phone = savedInstanceState.getString(KEY_PHONE);
            secretKey = savedInstanceState.getString(KEY_SECRET_KEY);
        }
    }

    private void setArguments(){
        phone = ConnectIdPasswordVerificationFragmentArgs.fromBundle(getArguments()).getPhone();
        secretKey = ConnectIdPasswordVerificationFragmentArgs.fromBundle(getArguments()).getSecret();
        callingClass = ConnectIdPasswordVerificationFragmentArgs.fromBundle(getArguments()).getCallingClass();
    }

    private void finish(boolean success, boolean forgot) {
        NavDirections directions = null;
        switch (callingClass) {
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD:
                if (success) {
                    directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidPin(ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN, ((ConnectIdActivity)activity).recoverPhone, ((ConnectIdActivity)activity).recoverSecret).setChange(true).setRecover(true);
                    if (forgot) {
                        directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE, getString(R.string.connect_recovery_alt_button), getString(R.string.connect_deactivate_account), phone, secretKey);
                    }
                } else {
                    directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragment.MethodRecoveryPrimary), ((ConnectIdActivity)activity).recoverPhone, ((ConnectIdActivity)activity).recoverPhone, "", null,false).setAllowChange(false);
                }
                break;
            case ConnectConstants.CONNECT_UNLOCK_PASSWORD:
                if (success) {
                    if (forgot) {
                        ((ConnectIdActivity)activity).forgotPassword = true;
                        directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidPhoneNo(ConnectConstants.METHOD_RECOVER_PRIMARY, null, ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE);
                    } else {
                        ((ConnectIdActivity)activity).forgotPassword = false;
                        FirebaseAnalyticsUtil.reportCccSignIn(AnalyticsParamValue.CCC_SIGN_IN_METHOD_PASSWORD);
                        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(activity);
                        user.setLastPinDate(new Date());
                        ConnectUserDatabaseUtil.storeUser(activity, user);
                        if (user.shouldRequireSecondaryPhoneVerification()) {
                            directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_MESSAGE, getString(R.string.connect_password_fail_button), getString(R.string.connect_recovery_alt_change_button), phone, secretKey);
                        } else {
                            ConnectIDManager.getInstance().setStatus(ConnectIDManager.ConnectIdStatus.LoggedIn);
                            ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                            activity.setResult(RESULT_OK);
                            activity.finish();
                        }
                    }
                }
                break;
        }
        if (directions != null) {
            Navigation.findNavController(binding.connectPasswordVerifyButton).navigate(directions);
        }
    }

    private void handleWrongPassword() {
        failureCount++;
        logRecoveryResult(false);
        binding.connectPasswordVerifyInput.setText("");

        int requestCode = PASSWORD_FAIL;
        int message = R.string.connect_password_fail_message;

        if (failureCount >= MaxFailures) {
            requestCode = PASSWORD_LOCK;
            message = R.string.connect_password_recovery_message;
        }
        NavDirections directions = ConnectIdPasswordVerificationFragmentDirections.actionConnectidPasswordToConnectidMessage(getString(R.string.connect_password_fail_title), getString(message), ConnectConstants.CONNECT_RECOVERY_WRONG_PASSWORD, getString(R.string.connect_recovery_success_button), null, phone, secretKey);

        Navigation.findNavController(binding.connectPasswordVerifyButton).navigate(directions);

    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PASSWORD);
    }

    private void onForgotPasswordClick() {
        finish(true, true);
    }

    private void onVerifyPasswordClick() {
        String password = Objects.requireNonNull(binding.connectPasswordVerifyInput.getText()).toString();
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(activity);
        if (user != null) {
            //If we have the password stored locally, no need for network call
            if (MessageDigest.isEqual(password.getBytes(), user.getPassword().getBytes())) {
                logRecoveryResult(true);
                finish(true, false);
            } else {
                handleWrongPassword();
            }
        } else {
            final Context context = activity;
            ApiConnectId.checkPassword(activity, phone, secretKey, password, new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    String username ;
                    String name ;
                    try {
                        String responseAsString = new String(
                                StreamsUtil.inputStreamToByteArray(responseData));
                        if (responseAsString.length() > 0) {
                            JSONObject json = new JSONObject(responseAsString);
                            username = json.getString(ConnectConstants.CONNECT_KEY_USERNAME);
                            name = json.getString(ConnectConstants.CONNECT_KEY_NAME);
                            ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(ConnectConstants.CONNECT_KEY_DB_KEY));

                            ConnectUserRecord user = new ConnectUserRecord(phone, username,
                                    password, name, "");

                            user.setSecondaryPhoneVerified(!json.has(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY) || json.isNull(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY));
                            if (!user.getSecondaryPhoneVerified()) {
                                user.setSecondaryPhoneVerifyByDate(DateUtils.parseDate(json.getString(ConnectConstants.CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY)));
                            }

                            ConnectUserDatabaseUtil.storeUser(context, user);
                        }
                    } catch (IOException e) {
                        Logger.exception("Parsing return from OTP request", e);
                    }catch (JSONException e){
                        throw new RuntimeException(e);
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
                    ConnectNetworkHelper.showOutdatedApiError(activity.getApplicationContext());
                }

                @Override
                public void processOldApiError() {
                    ConnectNetworkHelper.showOutdatedApiError(activity.getApplicationContext());
                }
            });
        }
    }

    private void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(activity, binding.connectPasswordVerifyInput);
    }

}
package org.commcare.fragments.connectId;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.SMSBroadcastReceiver;
import org.commcare.connect.SMSListener;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPhoneVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.CommCareNavController;
import org.commcare.utils.KeyboardHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.RECEIVER_NOT_EXPORTED;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdPhoneVerificationFragment#newInstance} factory method to
 * create an instance of requireActivity() fragment.
 */
public class ConnectIdPhoneVerificationFragment extends Fragment {
    public static final int MethodRegistrationPrimary = 1;
    public static final int MethodRecoveryPrimary = 2;
    public static final int MethodRecoveryAlternate = 3;
    public static final int MethodVerifyAlternate = 4;
    public static final int MethodUserDeactivate = 5;
    public static final int REQ_USER_CONSENT = 200;
    private int method;
    private String primaryPhone;
    private String username;
    private String password;
    private String recoveryPhone;
    private boolean deactivateButton;
    private int callingClass;
    private SMSBroadcastReceiver smsBroadcastReceiver;
    private DateTime smsTime = null;
    private ScreenConnectPhoneVerifyBinding binding;
    private static final String KEY_PHONE = "phone";
    private static final String KEY_METHOD = "method";
    private static final String KEY_ALLOWCHANGE = "allow_change";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_RECOVERY_PHONE = "recovery_phone";
    private static final String KEY_DEACTIVATE_BUTTON = "deactivate_button";
    private static final String KEY_CALLING_CLASS = "calling_class";
    private Activity activity;


    private final Handler taskHandler = new android.os.Handler();

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            int secondsToReset = -1;
            if (smsTime != null) {
                double elapsedMinutes = ((new DateTime()).getMillis() - smsTime.getMillis()) / 60000.0;
                int resendLimitMinutes = 2;
                double minutesRemaining = resendLimitMinutes - elapsedMinutes;
                if (minutesRemaining > 0) {
                    secondsToReset = (int)Math.ceil(minutesRemaining * 60);
                }
            }

            boolean allowResend = secondsToReset < 0;

            setResendEnabled(allowResend);

            String text = allowResend ?
                    getString(R.string.connect_verify_phone_resend) :
                    getString(R.string.connect_verify_phone_resend_wait, secondsToReset);

            binding.connectPhoneVerifyResend.setText(text);
            taskHandler.postDelayed(this, 100);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSavedState(savedInstanceState);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for requireActivity() fragment
        binding = ScreenConnectPhoneVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        activity = requireActivity();
        getArgument();
        binding.connectPhoneVerifyButton.setEnabled(false);
        buttonEnabled("");
        SmsRetrieverClient client = SmsRetriever.getClient(getActivity());// starting the SmsRetriever API
        client.startSmsUserConsent(null);

        handleDeactivateButton();

        updateMessage();

        requestSmsCode();

        startHandler();

        setListener();

        activity.setTitle(R.string.connect_verify_phone_title);
        return view;
    }

    private void handleDeactivateButton() {
        binding.connectDeactivateButton.setVisibility(!deactivateButton ? View.GONE : View.VISIBLE);
        binding.connectResendButton.setVisibility(View.GONE);
    }

    private void getArgument() {
        if (getArguments() != null) {
            method = Integer.parseInt(Objects.requireNonNull(ConnectIdPhoneVerificationFragmentArgs.fromBundle(getArguments()).getMethod()));
            primaryPhone = ConnectIdPhoneVerificationFragmentArgs.fromBundle(getArguments()).getPrimaryPhone();
            username = ConnectIdPhoneVerificationFragmentArgs.fromBundle(getArguments()).getUsername();
            password = ConnectIdPhoneVerificationFragmentArgs.fromBundle(getArguments()).getPassword();
            recoveryPhone = ConnectIdPhoneVerificationFragmentArgs.fromBundle(getArguments()).getSecondaryPhone();
            callingClass = ConnectIdPhoneVerificationFragmentArgs.fromBundle(getArguments()).getCallingClass();
            deactivateButton = ConnectIdPhoneVerificationFragmentArgs.fromBundle(getArguments()).getDeactivateButton();
        }
    }

    private void setListener() {
        binding.connectResendButton.setOnClickListener(arg0 -> requestSmsCode());
        binding.connectPhoneVerifyChange.setOnClickListener(arg0 -> changeNumber());
        binding.connectPhoneVerifyButton.setOnClickListener(arg0 -> verifySmsCode());
        binding.connectDeactivateButton.setOnClickListener(arg0 -> showYesNoDialog());

        binding.customOtpView.setOnOtpChangedListener(otp -> {
            setErrorMessage(null);
            buttonEnabled(otp);
        });
    }

    private void buttonEnabled(String code) {
        binding.connectPhoneVerifyButton.setEnabled(code.length() > 5);
    }

    @Override
    public void onStart() {
        super.onStart();
        registerBrodcastReciever();

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PHONE, primaryPhone);
        outState.putInt(KEY_METHOD, method);
        outState.putInt(KEY_CALLING_CLASS, callingClass);
        outState.putString(KEY_USERNAME, username);
        outState.putString(KEY_PASSWORD, password);
        outState.putString(KEY_RECOVERY_PHONE, recoveryPhone);
        outState.putBoolean(KEY_DEACTIVATE_BUTTON, deactivateButton);
    }

    private void loadSavedState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            primaryPhone = savedInstanceState.getString(KEY_PHONE);
            method = savedInstanceState.getInt(KEY_METHOD);
            callingClass = savedInstanceState.getInt(KEY_CALLING_CLASS);
            username = savedInstanceState.getString(KEY_USERNAME);
            password = savedInstanceState.getString(KEY_PASSWORD);
            recoveryPhone = savedInstanceState.getString(KEY_RECOVERY_PHONE);
            deactivateButton = savedInstanceState.getBoolean(KEY_DEACTIVATE_BUTTON);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_USER_CONSENT && (resultCode == RESULT_OK) && data != null) {
            String message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
            getOtpFromMessage(message);

        }
    }

    private void getOtpFromMessage(String message) {
        Pattern otpPattern = Pattern.compile("(|^)\\d{6}");
        Matcher matcher = otpPattern.matcher(message);
        if (matcher.find()) {
            binding.customOtpView.setOtp(matcher.group(0));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.connectPhoneVerifyChange.setVisibility(callingClass == ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE || callingClass == ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE ? View.VISIBLE : View.GONE);
        requestInputFocus();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopHandler();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            activity.unregisterReceiver(smsBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void registerBrodcastReciever() {
        smsBroadcastReceiver = new SMSBroadcastReceiver();

        smsBroadcastReceiver.setSmsListener(new SMSListener() {
            @Override
            public void onSuccess(Intent intent) {
                startActivityForResult(intent, REQ_USER_CONSENT);
            }
        });

        IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.registerReceiver(smsBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(smsBroadcastReceiver, intentFilter);
        }
    }

    private void setErrorMessage(String message) {
        if (message == null) {
            binding.connectPhoneVerifyError.setVisibility(View.GONE);
            binding.customOtpView.setErrorState(false);
        } else {
            binding.connectPhoneVerifyError.setVisibility(View.VISIBLE);
            binding.connectPhoneVerifyError.setText(message);
            binding.customOtpView.setErrorState(true);
        }
    }

    private void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(activity, binding.customOtpView);
    }

    private void setResendEnabled(boolean enabled) {
        binding.connectResendButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        binding.connectDeactivateButton.setVisibility(enabled ? View.GONE : (deactivateButton ? View.VISIBLE : View.GONE));
    }

    private void updateMessage() {
        String text;
        if (method == MethodUserDeactivate) {
            text = getString(R.string.connect_verify_phone_label_deactivate);
        } else {
            boolean alternate = method == MethodRecoveryAlternate || method == MethodVerifyAlternate;
            String phone = alternate ? recoveryPhone : primaryPhone;
            if (phone != null) {
                //Crop to last 4 digits
                phone = phone.substring(phone.length() - 4);
                text = getString(R.string.connect_verify_phone_label, phone);
            } else {
                //The primary phone is never missing
                text = getString(R.string.connect_verify_phone_label_secondary);
            }
        }
        binding.connectPhoneVerifyLabel.setText(text);
    }

    void startHandler() {
        taskHandler.postDelayed(runnable, 100);
    }

    void stopHandler() {
        taskHandler.removeCallbacks(runnable);
    }

    private void requestSmsCode() {
        smsTime = new DateTime();
        setErrorMessage(null);
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        if (json.has(ConnectConstants.CONNECT_KEY_SECRET)) {
                            password = json.getString(ConnectConstants.CONNECT_KEY_SECRET);
                        }

                        if (json.has(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE)) {
                            recoveryPhone = json.getString(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE);
                            updateMessage();
                        }
                    }
                } catch (IOException e) {
                    Logger.exception("Parsing return from OTP request", e);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void processFailure(int responseCode) {
                setErrorMessage("Error requesting SMS code");
                //Null out the last-requested time so user can request again immediately
                smsTime = null;
            }

            @Override
            public void processNetworkFailure() {
                setErrorMessage(getString(R.string.recovery_network_unavailable));
                //Null out the last-requested time so user can request again immediately
                smsTime = null;
            }

            @Override
            public void processTokenUnavailableError() {
                setErrorMessage(getString(R.string.recovery_network_token_unavailable));
            }

            @Override
            public void processTokenRequestDeniedError() {
                setErrorMessage(getString(R.string.recovery_network_token_request_rejected));
            }

            @Override
            public void processOldApiError() {
                setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        };

        switch (method) {
            case MethodRecoveryPrimary -> {
                ApiConnectId.requestRecoveryOtpPrimary(activity, username, callback);
            }
            case MethodRecoveryAlternate -> {
                ApiConnectId.requestRecoveryOtpSecondary(activity, username, password, callback);
            }
            case MethodVerifyAlternate -> {
                ApiConnectId.requestVerificationOtpSecondary(activity, username, password, callback);
            }
            default -> {
                ApiConnectId.requestRegistrationOtpPrimary(activity, username, password, callback);
            }
        }
    }

    private void verifySmsCode() {
        setErrorMessage(null);

        String token = binding.customOtpView.getOtpValue();
        String phone = username;
        final Context context = getContext();

        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                logRecoveryResult(true);

                try {
                    switch (method) {
                        case MethodRegistrationPrimary -> {
                            finish(true, false, null);
                        }
                        case MethodVerifyAlternate -> {
                            ConnectUserRecord user = ConnectIDManager.getInstance().getUser(activity.getApplicationContext());
                            user.setSecondaryPhoneVerified(true);
                            ConnectUserDatabaseUtil.storeUser(context, user);

                            finish(true, false, null);
                        }
                        case MethodRecoveryPrimary -> {
                            String secondaryPhone = null;
                            String responseAsString = new String(
                                    StreamsUtil.inputStreamToByteArray(responseData));
                            if (responseAsString.length() > 0) {
                                JSONObject json = new JSONObject(responseAsString);
                                secondaryPhone = json.has(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE) ? json.getString(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE) : null;
                            }

                            finish(true, false, secondaryPhone);
                        }
                        case MethodRecoveryAlternate -> {
                            String responseAsString = new String(
                                    StreamsUtil.inputStreamToByteArray(responseData));
                            JSONObject json = new JSONObject(responseAsString);

                            String username = json.getString(ConnectConstants.CONNECT_KEY_USERNAME);

                            String displayName = json.getString(ConnectConstants.CONNECT_KEY_NAME);

                            ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(ConnectConstants.CONNECT_KEY_DB_KEY));

                            resetPassword(context, phone, password, username, displayName);
                        }
                    }
                } catch (Exception e) {
                    Logger.exception("Parsing return from OTP verification", e);
                }
            }

            @Override
            public void processFailure(int responseCode) {
                logRecoveryResult(false);
                setErrorMessage(getString(R.string.connect_verify_phone_error));
            }

            @Override
            public void processNetworkFailure() {
                setErrorMessage(getString(R.string.recovery_network_unavailable));
            }

            @Override
            public void processTokenUnavailableError() {
                setErrorMessage(getString(R.string.recovery_network_token_unavailable));
            }

            @Override
            public void processTokenRequestDeniedError() {
                setErrorMessage(getString(R.string.recovery_network_token_request_rejected));
            }

            @Override
            public void processOldApiError() {
                setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        };

        switch (method) {
            case MethodRecoveryPrimary -> {
                ApiConnectId.confirmRecoveryOtpPrimary(getActivity(), username, password, token, callback);
            }
            case MethodRecoveryAlternate -> {
                ApiConnectId.confirmRecoveryOtpSecondary(activity, username, password, token, callback);
            }
            case MethodVerifyAlternate -> {
                ApiConnectId.confirmVerificationOtpSecondary(activity, username, password, token, callback);
            }
            default -> {
                ApiConnectId.confirmRegistrationOtpPrimary(activity, username, password, token, callback);
            }
        }
    }

    private void resetPassword(Context context, String phone, String secret, String username, String name) {
        //Auto-generate and send a new password
        String password = ConnectIDManager.getInstance().generatePassword();
        ApiConnectId.resetPassword(context, phone, secret, password, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                ConnectUserRecord user = new ConnectUserRecord(phone, username,
                        password, name, recoveryPhone);
                user.setSecondaryPhoneVerified(true);
                ConnectUserDatabaseUtil.storeUser(context, user);

                finish(true, false, null);
            }

            @Override
            public void processFailure(int responseCode) {
                Toast.makeText(context, getString(R.string.connect_recovery_failure),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(activity.getApplicationContext());
            }

            @Override
            public void processTokenUnavailableError() {
                setErrorMessage(getString(R.string.recovery_network_token_unavailable));
            }

            @Override
            public void processTokenRequestDeniedError() {
                setErrorMessage(getString(R.string.recovery_network_token_request_rejected));
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(activity.getApplicationContext());
            }
        });
    }

    private void logRecoveryResult(boolean success) {
        if (method != MethodRegistrationPrimary) {
            String methodParam = AnalyticsParamValue.CCC_RECOVERY_METHOD_PRIMARY_OTP;
            if (method == MethodRecoveryAlternate) {
                methodParam = AnalyticsParamValue.CCC_RECOVERY_METHOD_ALTERNATE_OTP;
            }
            FirebaseAnalyticsUtil.reportCccRecovery(success, methodParam);
        }
    }

    private void changeNumber() {
        finish(true, true, null);
    }

    private void finish(boolean success, boolean changeNumber, String secondaryPhone) {
        stopHandler();
        ConnectIdActivity refrenceActivity = (ConnectIdActivity)activity;
        if (method == MethodRecoveryPrimary) {
            (refrenceActivity).recoverSecret = password;
            if (secondaryPhone != null) {
                (refrenceActivity).recoveryAltPhone = secondaryPhone;
            }
        }
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        NavDirections directions = null;
        switch (callingClass) {
            case ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    if (changeNumber) {
                        directions = navigateToConnectidChangePhoneNo(primaryPhone);
                    } else {
                        directions = navigateToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN, user.getPrimaryPhone(), password, false, true);
                    }
                } else {
                    directions = navigateToConnectidBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    if (changeNumber) {
                        directions = navigateToConnectidPhoneNo(primaryPhone, ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE);
                    } else {
                        (refrenceActivity).recoveryAltPhone = secondaryPhone;
                        directions = navigateToConnectidPin(ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN, (refrenceActivity).recoverPhone, (refrenceActivity).recoverSecret, true, false);
                        if ((refrenceActivity).forgotPin) {
                            if ((refrenceActivity).forgotPassword) {
                                directions = navigateToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE, getString(R.string.connect_recovery_alt_button), null, username, password);

                            } else {
                                directions = navigateToConnectidPassword((refrenceActivity).recoverPhone, (refrenceActivity).recoverSecret, ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD);
                            }
                        }
                    }
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                if (success) {
                    if (!deactivateButton) {
                        directions = navigateToConnectidPin(ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN, (refrenceActivity).recoverPhone, (refrenceActivity).recoverSecret, true, true);
                    } else {
                        directions = navigateToConnectidUserDeactivateOtpVerify((refrenceActivity).recoverPhone, (refrenceActivity).recoverPhone, password);
                    }
                } else {
                    directions = navigateToConnectidPhoneVerifySelf(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, String.valueOf(
                            ConnectIdPhoneVerificationFragment.MethodRecoveryPrimary), (refrenceActivity).recoverPhone, (refrenceActivity).recoverPhone, "", "");
                }
            }
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE -> {
                if (success) {
                    ConnectIDManager.getInstance().setStatus(ConnectIDManager.ConnectIdStatus.LoggedIn);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                    activity.setResult(RESULT_OK);
                    activity.finish();
                }
            }
            case ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE -> {
                if (success) {
                    user.setSecondaryPhoneVerified(true);
                    ConnectUserDatabaseUtil.storeUser(activity, user);
                    ConnectIDManager.getInstance().setStatus(ConnectIDManager.ConnectIdStatus.LoggedIn);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                    activity.setResult(RESULT_OK);
                    activity.finish();
                }
            }
            case ConnectConstants.CONNECT_VERIFY_USER_DEACTIVATE -> {
                if (success) {
                    directions = navigateToConnectidMessage(getString(R.string.connect_deactivate_account), getString(R.string.connect_deactivate_account_deactivated), ConnectConstants.CONNECT_USER_DEACTIVATE_SUCCESS, getString(R.string.connect_deactivate_account_creation), null, username, password);
                }
            }
        }

        CommCareNavController.navigateSafely(Navigation.findNavController(binding.connectPhoneVerifyButton),directions);

    }

    private NavDirections navigateToConnectidPhoneNo(String phone, int phase) {
        return ConnectIdPhoneVerificationFragmentDirections.actionConnectidPhoneVerifyToConnectidSignupFragment().setPhone(phone).setCallingClass(phase);
    }
    private NavDirections navigateToConnectidChangePhoneNo(String phone) {
        return ConnectIdPhoneVerificationFragmentDirections.actionConnectidPhoneVerifyToConnectidPhoneNo(phone);
    }

    private NavDirections navigateToConnectidPin(int phase, String phone, String password, boolean recover, boolean change) {
        return ConnectIdPhoneVerificationFragmentDirections.actionConnectidPhoneVerifyToConnectidPin(phase, phone, password).setRecover(recover).setChange(change);
    }

    private NavDirections navigateToConnectidBiometricConfig(int phase) {
        return ConnectIdPhoneVerificationFragmentDirections.actionConnectidPhoneVerifyToConnectidBiometricConfig(phase);
    }

    private NavDirections navigateToConnectidMessage(String title, String message, int phase, String button1Text, String button2Text, String userName, String password) {
        return ConnectIdPhoneVerificationFragmentDirections.actionConnectidPhoneVerifyToConnectidMessage(title, message, phase, button1Text, button2Text, userName, password);
    }

    private NavDirections navigateToConnectidPassword(String phone, String password, int phase) {
        return ConnectIdPhoneVerificationFragmentDirections.actionConnectidPhoneVerifyToConnectidPassword(phone, password, phase);
    }

    private NavDirections navigateToConnectidUserDeactivateOtpVerify(String phone, String secondaryPhone, String password) {
        return ConnectIdPhoneVerificationFragmentDirections.actionConnectidPhoneVerifyToConnectidUserDeactivateOtpVerify(phone, secondaryPhone, password);
    }

    private NavDirections navigateToConnectidPhoneVerifySelf(int phase, String method, String phone, String secondaryPhone, String message, String button1Text) {
        return ConnectIdPhoneVerificationFragmentDirections.actionConnectidPhoneVerifySelf(phase, method, phone, secondaryPhone, message, button1Text, false);
    }

    private void showYesNoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme);
        builder.setTitle(R.string.connect_deactivate_dialog_title);
        builder.setMessage(R.string.connect_deactivate_dialog_description)
                .setPositiveButton(R.string.connect_payment_dialog_yes, (dialog, which) -> {
                    finish(true, true, null);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.connect_payment_dialog_no, (dialog, which) -> dialog.dismiss())
                .setCancelable(false);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}
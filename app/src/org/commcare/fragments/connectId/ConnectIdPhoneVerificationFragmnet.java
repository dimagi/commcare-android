package org.commcare.fragments.connectId;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.RECEIVER_NOT_EXPORTED;

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

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;

import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.SMSBroadcastReceiver;
import org.commcare.connect.SMSListener;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPhoneVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.CommCareNavController;
import org.commcare.utils.ConnectIdAppBarUtils;
import org.commcare.utils.KeyboardHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdPhoneVerificationFragmnet#newInstance} factory method to
 * create an instance of requireActivity() fragment.
 */
public class ConnectIdPhoneVerificationFragmnet extends Fragment {
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
    private boolean allowChange;
    private int callingClass;
    private SMSBroadcastReceiver smsBroadcastReceiver;
    private DateTime smsTime = null;

    private ScreenConnectPhoneVerifyBinding binding;

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
                    secondsToReset = (int) Math.ceil(minutesRemaining * 60);
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


    public ConnectIdPhoneVerificationFragmnet() {
        // Required empty public constructor
    }

    public static ConnectIdPhoneVerificationFragmnet newInstance() {
        return new ConnectIdPhoneVerificationFragmnet();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for requireActivity() fragment
        binding = ScreenConnectPhoneVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        binding.connectPhoneVerifyButton.setEnabled(false);
        buttonEnabled("");
        SmsRetrieverClient client = SmsRetriever.getClient(getActivity());// starting the SmsRetriever API
        client.startSmsUserConsent(null);


        if (getArguments() != null) {
            method = Integer.parseInt(Objects.requireNonNull(ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getMethod()));
            primaryPhone = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getPrimaryPhone();
            allowChange = (ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getAllowChange());
            username = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getUsername();
            password = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getPassword();
            recoveryPhone = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getSecondaryPhone();
            callingClass = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getCallingClass();
            deactivateButton = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getDeactivateButton();
        }

        handleDeactivateButton();

        updateMessage();

        requestSmsCode();

        startHandler();

        binding.connectResendButton.setOnClickListener(arg0 -> requestSmsCode());
        binding.connectPhoneVerifyChange.setOnClickListener(arg0 -> changeNumber());
        binding.connectPhoneVerifyButton.setOnClickListener(arg0 -> verifySmsCode());
        binding.connectDeactivateButton.setOnClickListener(arg0 -> showYesNoDialog());

        binding.customOtpView.setOnOtpChangedListener(otp -> {
            setErrorMessage(null);
            buttonEnabled(otp);
        });
        handleAppBar(view);
        return view;
    }

    private void handleAppBar(View view) {
        View appBarView = view.findViewById(R.id.commonAppBar);
        ConnectIdAppBarUtils.setTitle(appBarView, getString(R.string.connect_verify_phone_title));
        ConnectIdAppBarUtils.setBackButtonWithCallBack(appBarView, R.drawable.ic_connect_arrow_back, true, click -> {
            Navigation.findNavController(appBarView).popBackStack();
        });
    }

    private void handleDeactivateButton() {
        binding.connectDeactivateButton.setVisibility(!deactivateButton ? View.GONE : View.VISIBLE);
        binding.connectResendButton.setVisibility(View.GONE);
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
        binding.connectPhoneVerifyChange.setVisibility(allowChange ? View.VISIBLE : View.GONE);
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
            requireActivity().unregisterReceiver(smsBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

    }

    public void registerBrodcastReciever() {
        smsBroadcastReceiver = new SMSBroadcastReceiver();

        smsBroadcastReceiver.smsListener = new SMSListener() {
            @Override
            public void onSuccess(Intent intent) {
                startActivityForResult(intent, REQ_USER_CONSENT);
            }
        };

        IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(smsBroadcastReceiver, intentFilter,RECEIVER_NOT_EXPORTED);
        }else{
            requireActivity().registerReceiver(smsBroadcastReceiver, intentFilter);
        }
    }

    public void setErrorMessage(String message) {
        if (message == null) {
            binding.connectPhoneVerifyError.setVisibility(View.GONE);
            binding.customOtpView.setErrorState(false);
        } else {
            binding.connectPhoneVerifyError.setVisibility(View.VISIBLE);
            binding.connectPhoneVerifyError.setText(message);
            binding.customOtpView.setErrorState(true);
        }
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.customOtpView);
    }

    public void setResendEnabled(boolean enabled) {
        binding.connectResendButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        binding.connectDeactivateButton.setVisibility(enabled ? View.GONE : (deactivateButton ? View.VISIBLE : View.GONE));
    }

    public void updateMessage() {
        String text;
        if(method == MethodUserDeactivate) {
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

    public void requestSmsCode() {
        smsTime = new DateTime();
        setErrorMessage(null);
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        String key = ConnectConstants.CONNECT_KEY_SECRET;
                        if (json.has(key)) {
                            password = json.getString(key);
                        }

                        key = ConnectConstants.CONNECT_KEY_SECONDARY_PHONE;
                        if (json.has(key)) {
                            recoveryPhone = json.getString(key);
                            updateMessage();
                        }
                    }
                } catch (IOException | JSONException e) {
                    Logger.exception("Parsing return from OTP request", e);
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

        boolean isBusy;
        switch (method) {
            case MethodRecoveryPrimary -> {
               ApiConnectId.requestRecoveryOtpPrimary(requireActivity(), username, callback);
            }
            case MethodRecoveryAlternate -> {
             ApiConnectId.requestRecoveryOtpSecondary(requireActivity(), username, password, callback);
            }
            case MethodVerifyAlternate -> {
                ApiConnectId.requestVerificationOtpSecondary(requireActivity(), username, password, callback);
            }
            default -> {
               ApiConnectId.requestRegistrationOtpPrimary(requireActivity(), username, password, callback);
            }
        }

//        if (isBusy) {
//            Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
//        }
    }

    public void verifySmsCode() {
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
                            ConnectUserRecord user = ConnectManager.getUser(requireActivity().getApplicationContext());
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
                                String key = ConnectConstants.CONNECT_KEY_SECONDARY_PHONE;
                                secondaryPhone = json.has(key) ? json.getString(key) : null;
                            }

                            finish(true, false, secondaryPhone);
                        }
                        case MethodRecoveryAlternate -> {
                            String responseAsString = new String(
                                    StreamsUtil.inputStreamToByteArray(responseData));
                            JSONObject json = new JSONObject(responseAsString);

                            String key = ConnectConstants.CONNECT_KEY_USERNAME;
                            String username = json.has(key) ? json.getString(key) : "";

                            key = ConnectConstants.CONNECT_KEY_NAME;
                            String displayName = json.has(key) ? json.getString(key) : "";

                            key = ConnectConstants.CONNECT_KEY_DB_KEY;
                            if (json.has(key)) {
                                ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(key));
                            }

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
                ApiConnectId.confirmRecoveryOtpSecondary(requireActivity(), username, password, token, callback);
            }
            case MethodVerifyAlternate -> {
              ApiConnectId.confirmVerificationOtpSecondary(requireActivity(), username, password, token, callback);
            }
            default -> {
                ApiConnectId.confirmRegistrationOtpPrimary(requireActivity(), username, password, token, callback);
            }
        }
    }

    private void resetPassword(Context context, String phone, String secret, String username, String name) {
        //Auto-generate and send a new password
        String password = ConnectManager.generatePassword();
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
                ConnectNetworkHelper.showNetworkError(requireActivity());
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(requireActivity());
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenRequestDeniedException(requireActivity());
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(requireActivity());
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

    public void changeNumber() {
        finish(true, true, null);
    }

    public void finish(boolean success, boolean changeNumber, String secondaryPhone) {
        stopHandler();
        if (method == MethodRecoveryPrimary) {
            ConnectIdActivity.recoverSecret = password;
            if (secondaryPhone != null) {
                ConnectIdActivity.recoveryAltPhone = secondaryPhone;
            }
        }
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        NavDirections directions = null;
        switch (callingClass) {
            case ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    if (changeNumber) {
                        directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPhoneNo(ConnectConstants.METHOD_CHANGE_PRIMARY, primaryPhone, ConnectConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE);
                    } else {
                        directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN, user.getPrimaryPhone(), password).setRecover(false).setChange(true);
                    }
                } else {
                    directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    if (changeNumber) {
                        directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPhoneNo(ConnectConstants.METHOD_RECOVER_PRIMARY, primaryPhone, ConnectConstants.CONNECT_RECOVERY_PRIMARY_PHONE);
                    }else{
                        ConnectIdActivity.recoveryAltPhone = secondaryPhone;
                        directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPin(ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret).setRecover(true).setChange(false);
                        if (ConnectIdActivity.forgotPin) {
                            if (ConnectIdActivity.forgotPassword) {
                                directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE, getString(R.string.connect_recovery_alt_button), null, username, password);

                            } else {
                                directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPassword(ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret, ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD);
                            }
                        }
                    }
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                if (success) {
                    if (!deactivateButton) {
                        directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPin(ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret).setRecover(true).setChange(true);
                    } else {
                        directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidUserDeactivateOtpVerify(ConnectConstants.CONNECT_VERIFY_USER_DEACTIVATE, String.format(Locale.getDefault(), "%d",
                                ConnectIdPhoneVerificationFragmnet.MethodUserDeactivate), ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverPhone, password, "", false).setAllowChange(false);
                    }
                } else {
                    directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifySelf(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragmnet.MethodRecoveryPrimary), ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverPhone, "", "", false).setAllowChange(false);
                }
            }
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE -> {
                if (success) {
                    ConnectManager.setStatus(ConnectManager.ConnectIdStatus.LoggedIn);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                    requireActivity().setResult(RESULT_OK);
                    requireActivity().finish();
                }
            }
            case ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE -> {
                if (success) {
                    user.setSecondaryPhoneVerified(true);
                    ConnectUserDatabaseUtil.storeUser(requireActivity(), user);
                    ConnectManager.setStatus(ConnectManager.ConnectIdStatus.LoggedIn);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                    requireActivity().setResult(RESULT_OK);
                    requireActivity().finish();
                }
            }
            case ConnectConstants.CONNECT_VERIFY_USER_DEACTIVATE -> {
                if (success) {
                    directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidMessage(getString(R.string.connect_deactivate_account), getString(R.string.connect_deactivate_account_deactivated), ConnectConstants.CONNECT_USER_DEACTIVATE_SUCCESS, getString(R.string.connect_deactivate_account_creation), null, username, password);
                }
            }
        }

        CommCareNavController.navigateSafely(Navigation.findNavController(binding.connectPhoneVerifyButton),directions);

    }

    public void showYesNoDialog() {
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
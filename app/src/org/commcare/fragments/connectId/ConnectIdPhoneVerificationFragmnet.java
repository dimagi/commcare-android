package org.commcare.fragments.connectId;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
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
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.SMSBroadcastReceiver;
import org.commcare.connect.SMSListener;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPhoneVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.KeyboardHelper;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import static android.app.Activity.RESULT_OK;

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
    public static final int REQ_USER_CONSENT = 200;

    private int method;
    private String primaryPhone;
    private String username;
    private String password;
    private String recoveryPhone;
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


    public ConnectIdPhoneVerificationFragmnet() {
        // Required empty public constructor
    }

    public static ConnectIdPhoneVerificationFragmnet newInstance() {
        ConnectIdPhoneVerificationFragmnet fragment = new ConnectIdPhoneVerificationFragmnet();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for requireActivity() fragment
        binding= ScreenConnectPhoneVerifyBinding.inflate(inflater,container,false);
        View view = binding.getRoot();

        getActivity().setTitle(getString(R.string.connect_verify_phone_title));

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
        }

        updateMessage();

        requestSmsCode();

        startHandler();

        binding.connectPhoneVerifyResend.setOnClickListener(arg0 -> requestSmsCode());
        binding.connectPhoneVerifyChange.setOnClickListener(arg0 -> changeNumber());
        binding.connectPhoneVerifyButton.setOnClickListener(arg0 -> verifySmsCode());


        return view;
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

            binding.connectPhoneVerifyCode.setText(matcher.group(0));
        }


    }

    @Override
    public void onResume() {
        super.onResume();
        if (allowChange) {
            binding.connectPhoneVerifyChange.setVisibility(View.VISIBLE);
        }

        requestInputFocus();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            stopHandler();
            requireActivity().unregisterReceiver(smsBroadcastReceiver);
        }catch (IllegalArgumentException e){
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
        requireActivity().registerReceiver(smsBroadcastReceiver, intentFilter);
    }

    public void setErrorMessage(String message) {
        if (message == null) {
            binding.connectPhoneVerifyError.setVisibility(View.GONE);
        } else {
            binding.connectPhoneVerifyError.setVisibility(View.VISIBLE);
            binding.connectPhoneVerifyError.setText(message);
        }
    }

    public void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectPhoneVerifyCode);
    }

    public void setResendEnabled(boolean enabled) {
        binding.connectPhoneVerifyResend.setEnabled(enabled);
        binding.connectPhoneVerifyResend.setTextColor(enabled ? Color.BLUE : Color.GRAY);
    }


    public void updateMessage() {
        boolean alternate = method == MethodRecoveryAlternate || method == MethodVerifyAlternate;
        String text;
        String phone = alternate ? recoveryPhone : primaryPhone;
        if (phone != null) {
            //Crop to last 4 digits
            phone = phone.substring(phone.length() - 4);
            text = getString(R.string.connect_verify_phone_label, phone);
        } else {
            //The primary phone is never missing
            text = getString(R.string.connect_verify_phone_label_secondary);
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
            public void processFailure(int responseCode, IOException e) {
                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }
                setErrorMessage("Error requesting SMS code" + message);

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
            public void processOldApiError() {
                setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        };

        boolean isBusy;
        switch (method) {
            case MethodRecoveryPrimary -> {
                isBusy = !ApiConnectId.requestRecoveryOtpPrimary(requireActivity(), username, callback);
            }
            case MethodRecoveryAlternate -> {
                isBusy = !ApiConnectId.requestRecoveryOtpSecondary(requireActivity(), username, password, callback);
            }
            case MethodVerifyAlternate -> {
                isBusy = !ApiConnectId.requestVerificationOtpSecondary(requireActivity(), username, password, callback);
            }
            default -> {
                isBusy = !ApiConnectId.requestRegistrationOtpPrimary(requireActivity(), username, password, callback);
            }
        }

        if (isBusy) {
            Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public void verifySmsCode() {
        setErrorMessage(null);

        String token = binding.connectPhoneVerifyCode.getText().toString();
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
                            ConnectDatabaseHelper.storeUser(context, user);

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
            public void processFailure(int responseCode, IOException e) {
                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }
                logRecoveryResult(false);
                setErrorMessage(String.format("Error verifying SMS code. %s", message));
            }

            @Override
            public void processNetworkFailure() {
                setErrorMessage(getString(R.string.recovery_network_unavailable));
            }

            @Override
            public void processOldApiError() {
                setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        };

        boolean isBusy;
        switch (method) {
            case MethodRecoveryPrimary -> {
                isBusy = !ApiConnectId.confirmRecoveryOtpPrimary(getActivity(), username, password, token, callback);
            }
            case MethodRecoveryAlternate -> {
                isBusy = !ApiConnectId.confirmRecoveryOtpSecondary(requireActivity(), username, password, token, callback);
            }
            case MethodVerifyAlternate -> {
                isBusy = !ApiConnectId.confirmVerificationOtpSecondary(requireActivity(), username, password, token, callback);
            }
            default -> {
                isBusy = !ApiConnectId.confirmRegistrationOtpPrimary(requireActivity(), username, password, token, callback);
            }
        }

        if (isBusy) {
            Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
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
                ConnectDatabaseHelper.storeUser(context, user);

                finish(true, false, null);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(context, getString(R.string.connect_recovery_failure),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(requireActivity().getApplicationContext());
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(requireActivity().getApplicationContext());
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
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(getActivity());
        NavDirections directions = null;
        switch (callingClass) {
            case ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    if (changeNumber) {
                        directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPhone(ConnectConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE, ConnectConstants.METHOD_CHANGE_PRIMARY, secondaryPhone);
                    } else {
                        directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN, user.getPrimaryPhone(), password).setRecover(false).setChange(true);

                    }
                } else {
                    directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);

                }
            }
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    ConnectIdActivity.recoveryAltPhone = secondaryPhone;
                    directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPin(ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret).setRecover(true).setChange(false);
                    if (ConnectIdActivity.forgotPin) {
                        if (ConnectIdActivity.forgotPassword) {
                            directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE, getString(R.string.connect_recovery_alt_button), null);

                        } else {
                            directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPassword(ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret);

                        }
                    }
                }
            }
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                if(success){
                    directions = ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifyToConnectidPin(ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN, ConnectIdActivity.recoverPhone, ConnectIdActivity.recoverSecret).setRecover(true).setChange(true);

                }else{
                    directions= ConnectIdPhoneVerificationFragmnetDirections.actionConnectidPhoneVerifySelf(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE,String.format(Locale.getDefault(), "%d",
                            ConnectIdPhoneVerificationFragmnet.MethodRecoveryPrimary),ConnectIdActivity.recoverPhone,ConnectIdActivity.recoverPhone,"","").setAllowChange(false);
                }
            }
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE -> {
                if(success){
                    ConnectManager.setStatus(ConnectManager.ConnectIdStatus.LoggedIn);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                    requireActivity().setResult(RESULT_OK);
                    requireActivity().finish();
                }

            }
            case  ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE->{
                if(success){
                    user.setSecondaryPhoneVerified(true);
                    ConnectDatabaseHelper.storeUser(requireActivity(), user);
                    ConnectManager.setStatus(ConnectManager.ConnectIdStatus.LoggedIn);
                    ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
                    requireActivity().setResult(RESULT_OK);
                    requireActivity().finish();
                }
            }
        }

        if (directions != null) {
            Navigation.findNavController(binding.connectPhoneVerifyButton).navigate(directions);
        }
    }

}
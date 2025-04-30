package org.commcare.fragments.connectId;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;

import org.commcare.connect.ConnectConstants;
import org.commcare.connect.SMSBroadcastReceiver;
import org.commcare.connect.SMSListener;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectUserDeactivateOtpVerifyBinding;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Context.RECEIVER_NOT_EXPORTED;

public class ConnectIdUserDeactivateOTPVerificationFragment extends Fragment {
    public static final int REQ_USER_CONSENT = 200;
    private String primaryPhone;
    private String username;
    private String password;
    private DateTime smsTime = null;
    private ScreenConnectUserDeactivateOtpVerifyBinding binding;

    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_SMS_TIME = "sms_time";
    private final int RESEND_LIMIT_MINUTES = 2;


    private final Handler taskHandler = new Handler();

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            int secondsToReset = -1;
            if (smsTime != null) {
                double elapsedMinutes = ((new DateTime()).getMillis() - smsTime.getMillis()) / 60000.0;
                double minutesRemaining = RESEND_LIMIT_MINUTES - elapsedMinutes;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for requireActivity() fragment
        binding = ScreenConnectUserDeactivateOtpVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        binding.connectPhoneVerifyButton.setEnabled(false);
        getActivity().setTitle(getString(R.string.connect_verify_phone_title));
        buttonEnabled("");

        if (getArguments() != null) {
            primaryPhone = ConnectIdUserDeactivateOTPVerificationFragmentArgs.fromBundle(getArguments()).getPrimaryPhone();
            username = ConnectIdUserDeactivateOTPVerificationFragmentArgs.fromBundle(getArguments()).getUsername();
            password = ConnectIdUserDeactivateOTPVerificationFragmentArgs.fromBundle(getArguments()).getPassword();
        }

        loadSavedState(savedInstanceState);

        handleDeactivateButton();

        handleKeyboardType();

        updateMessage();

        requestSmsCode();

        setListener();

        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PHONE, primaryPhone);
        outState.putString(KEY_USER_NAME, username);
        outState.putString(KEY_PASSWORD, password);
        outState.putLong(KEY_SMS_TIME, smsTime.getMillis());
    }

    public void loadSavedState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            primaryPhone = savedInstanceState.getString(KEY_PHONE);
            password = savedInstanceState.getString(KEY_PASSWORD);
            username = savedInstanceState.getString(KEY_USER_NAME);
            smsTime = new DateTime(savedInstanceState.getLong(KEY_SMS_TIME));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void handleDeactivateButton() {
        binding.connectResendButton.setVisibility(View.GONE);
    }

    private void handleKeyboardType() {
        binding.connectPhoneVerifyCode.setInputType(InputType.TYPE_CLASS_TEXT);
    }


    private void buttonEnabled(String code) {
        binding.connectPhoneVerifyButton.setEnabled(code.length() > 5);
    }

    private void setListener() {
        binding.connectResendButton.setOnClickListener(arg0 -> requestSmsCode());
        binding.connectPhoneVerifyButton.setOnClickListener(arg0 -> verifySmsCode());
        binding.connectPhoneVerifyCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                buttonEnabled(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
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
        startHandler();
        requestInputFocus();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            stopHandler();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private void setErrorMessage(String message) {
        if (message == null) {
            binding.connectPhoneVerifyError.setVisibility(View.GONE);
        } else {
            binding.connectPhoneVerifyError.setVisibility(View.VISIBLE);
            binding.connectPhoneVerifyError.setText(message);
        }
    }

    private void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(requireActivity(), binding.connectPhoneVerifyCode);
    }

    private void setResendEnabled(boolean enabled) {
        binding.connectResendButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    private void updateMessage() {
        String text;
        String phone = primaryPhone;
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
                //Null out the last-requested time so user can request again immediately
                smsTime = null;
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

        ApiConnectId.requestInitiateAccountDeactivation(requireActivity(), username, password, callback);
    }

    private void verifySmsCode() {
        setErrorMessage(null);
        String token = binding.connectPhoneVerifyCode.getText().toString();
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                logRecoveryResult(true);
                stopHandler();
                Navigation.findNavController(binding.connectPhoneVerifyButton).navigate(
                        ConnectIdUserDeactivateOTPVerificationFragmentDirections.actionConnectidUserDeactivateOtpVerifyToConnectidMessage(
                                getString(R.string.connect_deactivate_account),
                                getString(R.string.connect_deactivate_account_deactivated),
                                ConnectConstants.CONNECT_USER_DEACTIVATE_SUCCESS,
                                getString(R.string.connect_deactivate_account_creation),
                                null, username, password));
            }

            @Override
            public void processFailure(int responseCode) {
                logRecoveryResult(false);
                setErrorMessage(getString(R.string.connect_verify_phone_error));
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
            public void processNetworkFailure() {
                setErrorMessage(getString(R.string.recovery_network_unavailable));
            }

            @Override
            public void processOldApiError() {
                setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        };

        ApiConnectId.confirmUserDeactivation(requireActivity(), username, password, token, callback);
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_USER_DEACTIVATE_OTP);
    }
}

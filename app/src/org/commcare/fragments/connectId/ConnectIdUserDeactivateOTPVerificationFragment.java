package org.commcare.fragments.connectId;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.commcare.connect.ConnectConstants;
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
import java.util.Locale;
import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdUserDeactivateOTPVerificationFragment#newInstance} factory method to
 * create an instance of requireActivity() fragment.
 */
public class ConnectIdUserDeactivateOTPVerificationFragment extends Fragment {
    public static final int REQ_USER_CONSENT = 200;
    private String primaryPhone;
    private String username;
    private String password;
    private DateTime smsTime = null;

    private ScreenConnectUserDeactivateOtpVerifyBinding binding;

    private final Handler taskHandler = new Handler();

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


    public ConnectIdUserDeactivateOTPVerificationFragment() {
        // Required empty public constructor
    }

    public static ConnectIdUserDeactivateOTPVerificationFragment newInstance() {
        ConnectIdUserDeactivateOTPVerificationFragment fragment = new ConnectIdUserDeactivateOTPVerificationFragment();
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
        binding = ScreenConnectUserDeactivateOtpVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        binding.connectPhoneVerifyButton.setEnabled(false);
        getActivity().setTitle(getString(R.string.connect_verify_phone_title));
        buttonEnabled("");

        if (getArguments() != null) {
            int method = Integer.parseInt(Objects.requireNonNull(ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getMethod()));
            primaryPhone = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getPrimaryPhone();
            boolean allowChange = (ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getAllowChange());
            username = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getUsername();
            password = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getPassword();
            int callingClass = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getCallingClass();
            boolean deactivateButton = ConnectIdPhoneVerificationFragmnetArgs.fromBundle(getArguments()).getDeactivateButton();
        }

        handleDeactivateButton();

        handleKeyboardType();

        updateMessage();

        requestSmsCode();

        startHandler();

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
        return view;
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

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        requestInputFocus();
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
        binding.connectResendButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    public void updateMessage() {
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

        ApiConnectId.requestInitiateAccountDeactivation(requireActivity(), username, password, callback);
    }

    public void verifySmsCode() {
        setErrorMessage(null);
        String token = binding.connectPhoneVerifyCode.getText().toString();
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                logRecoveryResult(true);
                try {
                    stopHandler();
                    Navigation.findNavController(binding.connectPhoneVerifyButton).navigate(
                            ConnectIdUserDeactivateOTPVerificationFragmentDirections.actionConnectidUserDeactivateOtpVerifyToConnectidMessage(
                                    getString(R.string.connect_deactivate_account),
                                    getString(R.string.connect_deactivate_account_deactivated),
                                    ConnectConstants.CONNECT_USER_DEACTIVATE_SUCCESS,
                                    getString(R.string.connect_deactivate_account_creation),
                                    null, username, password));
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
                setErrorMessage(getString(R.string.connect_verify_phone_error));
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
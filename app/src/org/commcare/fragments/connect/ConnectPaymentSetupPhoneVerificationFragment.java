package org.commcare.fragments.connect;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.SMSBroadcastReceiver;
import org.commcare.connect.SMSListener;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectPaymentPhoneVerifyBinding;
import org.commcare.utils.KeyboardHelper;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectPaymentSetupPhoneVerificationFragment#newInstance} factory method to
 * create an instance of requireActivity() fragment.
 */
public class ConnectPaymentSetupPhoneVerificationFragment extends Fragment {
    public static final int REQ_USER_CONSENT = 200;
    private String primaryPhone;
    private String paymentProfileName;
    private String username;
    private String password;
    private SMSBroadcastReceiver smsBroadcastReceiver;
    private DateTime smsTime = null;

    private ScreenConnectPaymentPhoneVerifyBinding binding;

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


    public ConnectPaymentSetupPhoneVerificationFragment() {
        // Required empty public constructor
    }

    public static ConnectPaymentSetupPhoneVerificationFragment newInstance() {
        ConnectPaymentSetupPhoneVerificationFragment fragment = new ConnectPaymentSetupPhoneVerificationFragment();
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
        binding = ScreenConnectPaymentPhoneVerifyBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        binding.connectPhoneVerifyButton.setEnabled(false);
        getActivity().setTitle(getString(R.string.connect_verify_phone_title));
        buttonEnabled("");
        SmsRetrieverClient client = SmsRetriever.getClient(getActivity());// starting the SmsRetriever API
        client.startSmsUserConsent(null);


        if (getArguments() != null) {
            primaryPhone = ConnectPaymentSetupPhoneVerificationFragmentArgs.fromBundle(getArguments()).getPhone();
            paymentProfileName = ConnectPaymentSetupPhoneVerificationFragmentArgs.fromBundle(getArguments()).getPaymentProfileName();
            username = ConnectPaymentSetupPhoneVerificationFragmentArgs.fromBundle(getArguments()).getUsername();
            password = ConnectPaymentSetupPhoneVerificationFragmentArgs.fromBundle(getArguments()).getPassword();
        }

        updateMessage();

        startHandler();

        binding.connectResendOtpButton.setOnClickListener(arg0 -> requestSmsCode());
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

    private void requestSmsCode() {
        setErrorMessage(null);
        boolean isBusy = !ApiConnectId.paymentInfo(requireActivity(), primaryPhone, username, password, paymentProfileName, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                updateMessage();
                startHandler();
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                smsTime = null;
                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }
                setErrorMessage("Error sending SMS");
            }

            @Override
            public void processNetworkFailure() {
                smsTime = null;
                setErrorMessage(getString(R.string.recovery_network_unavailable));
            }

            @Override
            public void processOldApiError() {
                setErrorMessage(getString(R.string.recovery_network_outdated));
            }
        });

        if (isBusy) {
            Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void buttonEnabled(String code) {
        binding.connectPhoneVerifyButton.setEnabled(!code.isEmpty() && code.length() > 5);
    }

    @Override
    public void onStart() {
        super.onStart();
        registerBroadcastReceiver();
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
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

    }

    public void registerBroadcastReceiver() {
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
        binding.connectResendOtpButton.setEnabled(enabled);
        binding.connectResendOtpButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }


    public void updateMessage() {
        String phone = primaryPhone;
        phone = phone.substring(phone.length() - 4);
        String text = getString(R.string.connect_verify_phone_label, phone);
        binding.connectPhoneVerifyLabel.setText(text);
    }

    void startHandler() {
        smsTime = new DateTime();
        taskHandler.postDelayed(runnable, 100);
    }

    void stopHandler() {
        taskHandler.removeCallbacks(runnable);
    }

    public void verifySmsCode() {
        setErrorMessage(null);

        String token = binding.connectPhoneVerifyCode.getText().toString();
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(getActivity());

        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    stopHandler();
                    Navigation.findNavController(binding.connectPhoneVerifyButton).navigate(
                            ConnectPaymentSetupPhoneVerificationFragmentDirections.actionConnectPaymentSetupPhoneVerificationFragmentToConnectJobsListFragment());
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
                setErrorMessage("Error verifying SMS code");
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
        isBusy = !ApiConnectId.confirmPaymentInfo(requireActivity(), primaryPhone, username, password, token, callback);
        if (isBusy) {
            Toast.makeText(requireActivity(), R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }
}
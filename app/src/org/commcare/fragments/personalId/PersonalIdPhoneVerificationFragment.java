package org.commcare.fragments.personalId;

import android.app.Activity;
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
import com.google.firebase.auth.FirebaseUser;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.SMSBroadcastReceiver;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiPersonalId;
import org.commcare.connect.network.PersonalIdApiErrorHandler;
import org.commcare.connect.network.PersonalIdApiHandler;
import org.commcare.connect.network.parser.AddOrVerifyNameParser;
import org.commcare.connect.network.parser.StartConfigurationResponseParser;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidPhoneVerifyBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.OtpManager;
import org.commcare.utils.OtpVerificationCallback;
import org.joda.time.DateTime;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.RECEIVER_NOT_EXPORTED;

public class PersonalIdPhoneVerificationFragment extends Fragment {

    private static final int REQ_USER_CONSENT = 200;
    private static final String KEY_PHONE = "phone";

    private Activity activity;
    private String primaryPhone;
    private DateTime otpRequestTime;
    private SMSBroadcastReceiver smsBroadcastReceiver;
    private ScreenPersonalidPhoneVerifyBinding binding;
    private final Handler resendTimerHandler = new Handler();
    private OtpManager otpManager;

    private final Runnable resendTimerRunnable = new Runnable() {
        @Override
        public void run() {
            updateResendButtonState();
            resendTimerHandler.postDelayed(this, 100);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            primaryPhone = savedInstanceState.getString(KEY_PHONE);
        }
        initOtpManager();
    }

    private void initOtpManager() {
        OtpVerificationCallback otpCallback = new OtpVerificationCallback() {
            @Override
            public void onCodeSent(String verificationId) {
                Toast.makeText(requireContext(), "OTP Sent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(FirebaseUser user) {
                Toast.makeText(requireContext(), "OTP Verified: " + user.getPhoneNumber(), Toast.LENGTH_SHORT).show();
                user.getIdToken(false).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String idToken = task.getResult().getToken();
                        validateFirebaseIdToken(idToken);
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(requireContext(), "OTP Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                displayOtpError(errorMessage);
            }
        };

        // Pass the Activity and callback to the OtpManager (no need to manually build PhoneAuthOptions)
        otpManager = new OtpManager(requireActivity(), otpCallback);
    }



    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ScreenPersonalidPhoneVerifyBinding.inflate(inflater, container, false);
        activity = requireActivity();
        getPhoneNumberFromArguments();

        setupInitialState();
        setupSmsRetriever();
        setupListeners();
        startResendTimer();

        activity.setTitle(R.string.connect_verify_phone_title);
        return binding.getRoot();
    }

    private void validateFirebaseIdToken(String firebaseIdToken) {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        new PersonalIdApiHandler() {
            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                navigateToNameEntry();
            }
            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode) {
                navigateFailure(failureCode);
            }
        }.validateFirebaseIdToken(requireActivity(),user.getName(),user.getPassword(),firebaseIdToken);
    }

    private void getPhoneNumberFromArguments() {
        if (getArguments() != null) {
            primaryPhone = PersonalIdPhoneVerificationFragmentArgs.fromBundle(getArguments()).getPrimaryPhone();
        }
    }

    private void setupInitialState() {
        binding.connectPhoneVerifyButton.setEnabled(false);
        updateVerificationMessage();
        requestOtp();
    }

    private void setupSmsRetriever() {
        SmsRetrieverClient client = SmsRetriever.getClient(activity);
        client.startSmsUserConsent(null);
    }

    private void setupListeners() {
        binding.connectResendButton.setOnClickListener(v -> requestOtp());
        binding.connectPhoneVerifyChange.setOnClickListener(v -> navigateToPhoneEntry());
        binding.connectPhoneVerifyButton.setOnClickListener(v -> verifyOtp());

        binding.customOtpView.setOnOtpChangedListener(otp -> {
            clearOtpError();
            toggleVerifyButton(otp);
        });
    }

    private void toggleVerifyButton(String otp) {
        binding.connectPhoneVerifyButton.setEnabled(otp.length() > 5);
    }

    private void clearOtpError() {
        binding.connectPhoneVerifyError.setVisibility(View.GONE);
        binding.customOtpView.setErrorState(false);
    }

    private void displayOtpError(String message) {
        binding.connectPhoneVerifyError.setVisibility(View.VISIBLE);
        binding.connectPhoneVerifyError.setText(message);
        binding.customOtpView.setErrorState(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        registerSmsBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();
        KeyboardHelper.showKeyboardOnInput(activity, binding.customOtpView);
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
    public void onStop() {
        super.onStop();
        stopResendTimer();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PHONE, primaryPhone);
    }

    private void registerSmsBroadcastReceiver() {
        smsBroadcastReceiver = new SMSBroadcastReceiver();
        smsBroadcastReceiver.setSmsListener(intent -> startActivityForResult(intent, REQ_USER_CONSENT));

        IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.registerReceiver(smsBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(smsBroadcastReceiver, intentFilter);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_USER_CONSENT && resultCode == RESULT_OK && data != null) {
            extractOtpFromMessage(data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE));
        }
    }

    private void extractOtpFromMessage(String message) {
        Pattern pattern = Pattern.compile("\\b\\d{6}\\b");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            binding.customOtpView.setOtp(matcher.group(0));
        }
    }

    private void updateVerificationMessage() {
        if (primaryPhone != null && primaryPhone.length() >= 4) {
            String lastFourDigits = primaryPhone.substring(primaryPhone.length() - 4);
            binding.connectPhoneVerifyLabel.setText(getString(R.string.connect_verify_phone_label, lastFourDigits));
        }
    }

    private void requestOtp() {
        otpRequestTime = new DateTime();
        clearOtpError();
        otpManager.requestOtp(primaryPhone);
    }

    private void verifyOtp() {
        clearOtpError();
        String otpCode = binding.customOtpView.getOtpValue();

        if (otpCode.isEmpty()) {
            Toast.makeText(requireContext(), "Enter OTP", Toast.LENGTH_SHORT).show();
        } else {
            otpManager.submitOtp(otpCode);
            logOtpVerification(true);
        }
    }

    private void logOtpVerification(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PRIMARY_OTP);
    }

    private void startResendTimer() {
        resendTimerHandler.postDelayed(resendTimerRunnable, 100);
    }

    private void stopResendTimer() {
        resendTimerHandler.removeCallbacks(resendTimerRunnable);
    }

    private void updateResendButtonState() {
        boolean canResend = true;
        int secondsRemaining = 0;

        if (otpRequestTime != null) {
            double minutesElapsed = (new DateTime().getMillis() - otpRequestTime.getMillis()) / 60000.0;
            double minutesRemaining = 2 - minutesElapsed;
            if (minutesRemaining > 0) {
                canResend = false;
                secondsRemaining = (int) Math.ceil(minutesRemaining * 60);
            }
        }

        binding.connectResendButton.setVisibility(canResend ? View.VISIBLE : View.GONE);
        String label = canResend
                ? getString(R.string.connect_verify_phone_resend)
                : getString(R.string.connect_verify_phone_resend_wait, secondsRemaining);
        binding.connectPhoneVerifyResend.setText(label);
    }

    private void navigateToPhoneEntry() {
        NavDirections directions = PersonalIdPhoneVerificationFragmentDirections.actionPersonalidOtpPageToPersonalidPhoneFragment();
        Navigation.findNavController(binding.connectResendButton).navigate(directions);
    }

    private void navigateToNameEntry() {
        NavDirections directions = PersonalIdPhoneVerificationFragmentDirections.actionPersonalidOtpPageToPersonalidName();
        Navigation.findNavController(binding.connectResendButton).navigate(directions);
    }

    private void navigateFailure(PersonalIdApiHandler.PersonalIdApiErrorCodes failureCode) {
        PersonalIdApiErrorHandler.handle(requireActivity(), failureCode);
    }
}

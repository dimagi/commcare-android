package org.commcare.fragments.personalId;

import android.app.Activity;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.google.android.gms.auth.api.phone.SmsRetriever;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.SMSBroadcastReceiver;
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler;
import org.commcare.connect.network.connectId.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidPhoneVerifyBinding;
import org.commcare.util.LogTypes;
import org.commcare.utils.FirebaseAuthService;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.OtpAuthService;
import org.commcare.utils.OtpErrorType;
import org.commcare.utils.OtpManager;
import org.commcare.utils.OtpVerificationCallback;
import org.commcare.utils.PersonalIdAuthService;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PersonalIdPhoneVerificationFragment extends Fragment {
    private static final String KEY_PHONE = "phone";

    private Activity activity;
    private String primaryPhone;
    private DateTime otpRequestTime;
    private SMSBroadcastReceiver smsBroadcastReceiver;
    private ScreenPersonalidPhoneVerifyBinding binding;
    private final Handler resendTimerHandler = new Handler();
    private OtpManager otpManager;
    private PersonalIdSessionData personalIdSessionData;
    OtpVerificationCallback otpCallback;
    private ActivityResultLauncher<Intent> smsConsentLauncher;



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
        activity = requireActivity();
        personalIdSessionData = new ViewModelProvider(requireActivity()).get(
                PersonalIdSessionDataViewModel.class).getPersonalIdSessionData();
        primaryPhone = personalIdSessionData.getPhoneNumber();
        if (savedInstanceState != null) {
            primaryPhone = savedInstanceState.getString(KEY_PHONE);
        }
        initOtpManager();
    }

    private void initOtpManager() {
        otpCallback = new OtpVerificationCallback() {
            @Override
            public void onCodeSent(String verificationId) {
                if (otpCallback == null) return;
                Toast.makeText(requireContext(), getString(R.string.connect_otp_sent), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCodeVerified(String code) {
                if (otpCallback == null) return;
                Toast.makeText(requireContext(), getString(R.string.connect_otp_verified), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess() {
                navigateToNameEntry();
            }

            @Override
            public void onFailure(OtpErrorType errorType, @Nullable String errorMessage) {
                if (otpCallback == null) return;
                String userMessage = switch (errorType) {
                    case INVALID_CREDENTIAL -> getString(R.string.personalid_incorrect_otp);
                    case TOO_MANY_REQUESTS -> getString(R.string.personalid_too_many_otp_attempts);
                    case MISSING_ACTIVITY -> getString(R.string.personalid_otp_missing_activity);
                    case VERIFICATION_FAILED -> getString(R.string.personalid_otp_verification_failed);
                    default ->
                            getString(R.string.personalid_otp_verification_failed_generic) + (errorMessage != null ? errorMessage : "Unknown error");
                };
                displayOtpError(userMessage);
                binding.connectPhoneVerifyButton.setEnabled(false);
            }
        };
        otpManager = new OtpManager(activity, personalIdSessionData, otpCallback);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ScreenPersonalidPhoneVerifyBinding.inflate(inflater, container, false);
        setupInitialState();
        setupListeners();

        activity.setTitle(R.string.connect_verify_phone_title);
        return binding.getRoot();
    }


    private void setupInitialState() {
        binding.connectPhoneVerifyButton.setEnabled(false);
        updateVerificationMessage();
        requestOtp();
    }

    private void setupListeners() {
        binding.connectResendButton.setOnClickListener(v -> requestOtp());
        binding.connectPhoneVerifyChange.setOnClickListener(v -> navigateToPhoneEntry());
        binding.connectPhoneVerifyButton.setOnClickListener(v -> verifyOtp());

        binding.customOtpView.setOnOtpChangedListener(otp -> {
            clearOtpError();
            toggleVerifyButton(otp);
        });

        smsConsentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String message = result.getData().getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
                        String otp = extractOtp(message);
                        binding.customOtpView.setOtp(otp); // Autofill OTP
                    }
                }
        );
    }

    private void toggleVerifyButton(String otp) {
        binding.connectPhoneVerifyButton.setEnabled(otp.length() > 5);
    }

    private void clearOtpError() {
        binding.connectPhoneVerifyError.setVisibility(View.GONE);
        binding.customOtpView.setErrorState(false);
    }

    private void displayOtpError(String message) {
        if (message != null && !message.isEmpty()){
            binding.connectPhoneVerifyError.setVisibility(View.VISIBLE);
            binding.connectPhoneVerifyError.setText(message);
            binding.customOtpView.setErrorState(true);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        startSmsUserConsent();
        registerSmsReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();
        startResendTimer();
        KeyboardHelper.showKeyboardOnInput(activity, binding.customOtpView);
    }

    private void startSmsUserConsent() {
        SmsRetriever.getClient(requireContext())
                .startSmsUserConsent(null); // null = any sender
    }

    private void registerSmsReceiver() {
        IntentFilter filter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        smsBroadcastReceiver = new SMSBroadcastReceiver(smsConsentLauncher);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(smsBroadcastReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            requireActivity().registerReceiver(smsBroadcastReceiver, filter);

        }
    }

    private String extractOtp(String message) {
        Pattern p = Pattern.compile("\\b\\d{6}\\b");
        Matcher m = p.matcher(message);
        if (m.find()) {
            return m.group(0);
        } else {
            Logger.log(LogTypes.TYPE_EXCEPTION, "OTP pattern dose't match");
            return "";
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        requireContext().unregisterReceiver(smsBroadcastReceiver);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopResendTimer();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        otpCallback = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PHONE, primaryPhone);
    }

    private void updateVerificationMessage() {
        if (primaryPhone != null && primaryPhone.length() >= 4) {
            String lastFourDigits = primaryPhone.substring(primaryPhone.length() - 4);
            binding.connectPhoneVerifyLabel.setText(getString(R.string.connect_verify_phone_label, lastFourDigits));
        }
    }

    private void requestOtp() {
        clearOtpError();
        otpRequestTime = new DateTime();
        otpManager.requestOtp(primaryPhone);
    }

    private void verifyOtp() {
        binding.connectPhoneVerifyButton.setEnabled(false);
        clearOtpError();
        String otpCode = binding.customOtpView.getOtpValue();

        if (otpCode.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.connect_enter_otp), Toast.LENGTH_SHORT).show();
        } else {
            otpManager.verifyOtp(otpCode);
        }
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

    private void handleFailure(PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
        displayOtpError(PersonalIdApiErrorHandler.handle(requireActivity(), failureCode, t));
    }
}

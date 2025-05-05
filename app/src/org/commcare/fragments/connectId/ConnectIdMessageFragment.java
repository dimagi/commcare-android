package org.commcare.fragments.connectId;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.commcare.activities.SettingsHelper;
import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenConnectMessageBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import static android.app.Activity.RESULT_OK;

public class ConnectIdMessageFragment extends BottomSheetDialogFragment {
    private String title;
    private String message;
    private String buttonText;
    private String button2Text;
    private String userName;
    private String password;
    private boolean isDismissible = true;
    private int callingClass;
    private static final String KEY_TITLE = "title";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_BUTTON2_TEXT = "button2_text";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_CALLING_CLASS = "calling_class";
    private static final String KEY_IS_DISMISSIBLE = "is_dismissible";
    private ScreenConnectMessageBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = ScreenConnectMessageBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        loadSavedState(savedInstanceState);
        binding.connectMessageButton.setOnClickListener(v -> handleContinueButtonPress(false));
        binding.connectMessageButton2.setOnClickListener(v -> handleContinueButtonPress(true));
        loadArguments();
        this.setCancelable(isDismissible);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.connectMessageTitle.setText(title);
        binding.connectMessageMessage.setText(message);
        binding.connectMessageButton.setText(buttonText);
        setButton2Text(button2Text);
    }

    private void loadSavedState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            title = savedInstanceState.getString(KEY_TITLE);
            message = savedInstanceState.getString(KEY_MESSAGE);
            button2Text = savedInstanceState.getString(KEY_BUTTON2_TEXT);
            userName = savedInstanceState.getString(KEY_USER_NAME);
            password = savedInstanceState.getString(KEY_PASSWORD);
            callingClass = savedInstanceState.getInt(KEY_CALLING_CLASS);
            isDismissible = savedInstanceState.getBoolean(KEY_IS_DISMISSIBLE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_TITLE, title);
        outState.putString(KEY_MESSAGE, message);
        outState.putString(KEY_BUTTON2_TEXT, button2Text);
        outState.putString(KEY_USER_NAME, userName);
        outState.putString(KEY_PASSWORD, password);
        outState.putInt(KEY_CALLING_CLASS, callingClass);
        outState.putBoolean(KEY_IS_DISMISSIBLE, isDismissible);
    }

    private void loadArguments() {
        title = org.commcare.fragments.connectId.ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getTitle();
        message = org.commcare.fragments.connectId.ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getMessage();
        buttonText = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButtonText();
        callingClass = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getCallingClass();
        userName = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getPhone();
        password = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getPassword();
        isDismissible = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getIsDismissible();
        if (ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text() != null && !ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text().isEmpty()) {
            button2Text = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setButton2Text(String buttonText) {
        boolean show = buttonText != null;
        binding.connectMessageButton2.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.connectMessageButton2.setText(buttonText);
        }
    }

    private void handleContinueButtonPress(boolean secondButton) {
        finish(secondButton);
    }

    private void finish(boolean secondButton) {
        NavDirections directions = null;
        Activity activity = requireActivity();
        ConnectIdActivity connectIdActivity = (ConnectIdActivity)activity;
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        switch (callingClass) {
            case ConnectConstants.CONNECT_REGISTRATION_SUCCESS, ConnectConstants.CONNECT_RECOVERY_SUCCESS:
                successFlow(activity);
                break;
            //CONNECT_RECOVERY_ALT_PHONE_MESSAGE
            case ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE:
                if (secondButton) {
                    directions = navigateToDeactivateAccount();
                } else {
                    directions = navigateToPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE, ConnectIdPhoneVerificationFragment.MethodRecoveryAlternate, null, connectIdActivity.recoverPhone, connectIdActivity.recoverSecret, ((ConnectIdActivity)getActivity()).recoveryAltPhone);
                }
                break;
            case ConnectConstants.CONNECT_BIOMETRIC_ENROLL_FAIL:
                SettingsHelper.launchSecuritySettings(activity);
                break;
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD:
                if (connectIdActivity.forgotPassword) {
                    directions = navigateToMessage(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE, getString(R.string.connect_recovery_alt_button), null, userName, password);
                } else {
                    directions = navigateToPin(ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN, connectIdActivity.recoverPhone, connectIdActivity.recoverSecret, true, true);
                }
                break;
            case ConnectConstants.CONNECT_RECOVERY_WRONG_PASSWORD:
                directions = navigateToPassword(connectIdActivity.recoverPhone, connectIdActivity.recoverSecret, ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD);
                break;
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_MESSAGE:
                if (secondButton) {
                    directions = navigateToSecondaryPhoneFragment(ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE);
                } else {
                    directions = navigateToPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, ConnectIdPhoneVerificationFragment.MethodVerifyAlternate, null, user.getUserId(), user.getPassword(), null);
                }

                break;
            //CONNECT_RECOVERY_WRONG_PIN
            case ConnectConstants.CONNECT_RECOVERY_WRONG_PIN:
                if (ConnectIDManager.getInstance().getFailureAttempt() > 2) {
                    directions = navigateToPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE, ConnectIdPhoneVerificationFragment.MethodRecoveryAlternate, null, connectIdActivity.recoverPhone, connectIdActivity.recoverSecret, ((ConnectIdActivity)getActivity()).recoveryAltPhone);
                    ConnectIDManager.getInstance().setFailureAttempt(0);
                } else {
                    directions = navigateToPin(ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN, connectIdActivity.recoverPhone, connectIdActivity.recoverSecret, false, true);
                }

                break;
            case ConnectConstants.CONNECT_REGISTRATION_WRONG_PIN:
                if (ConnectIDManager.getInstance().getFailureAttempt() > 2) {
                    directions = navigateToPin(ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN, user.getPrimaryPhone(), "", true, false);
                } else {
                    directions = navigateToPin(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, user.getPrimaryPhone(), "", false, false);
                }

                break;
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE:
                if (secondButton) {
                    directions = navigateToSecondaryPhoneFragment(ConnectConstants.CONNECT_VERIFY_ALT_PHONE_CHANGE);
                } else {
                    directions = navigateToPhoneVerify(ConnectConstants.CONNECT_VERIFY_ALT_PHONE, ConnectIdPhoneVerificationFragment.MethodVerifyAlternate, null, user.getUserId(), user.getPassword(), null);
                }

                break;
            case ConnectConstants.CONNECT_USER_DEACTIVATE_CONFIRMATION:
                if (!secondButton) {
                    directions = navigateToUserDeactivateOtpVerify(connectIdActivity.recoverPhone, userName, password);
                } else {
                    NavHostFragment.findNavController(this).popBackStack();
                }

                break;
            case ConnectConstants.CONNECT_USER_DEACTIVATE_SUCCESS:
                if (!secondButton) {
                    ConnectIDManager.getInstance().forgetUser(AnalyticsParamValue.CCC_FORGOT_USER_DEACTIVATION);
                    activity.finish();
                }

                break;
        }
        if (directions != null) {
            NavHostFragment.findNavController(this).navigate(directions);

        }
    }

    private NavDirections navigateToDeactivateAccount() {
        return ConnectIdMessageFragmentDirections.actionConnectidMessageSelf(getString(R.string.connect_deactivate_account), getString(R.string.connect_deactivate_account_message), ConnectConstants.CONNECT_USER_DEACTIVATE_CONFIRMATION, getString(R.string.connect_deactivate_account_delete), getString(R.string.connect_deactivate_account_go_back), userName, password);
    }

    private NavDirections navigateToMessage(String title, String message, int callingClass, String button2Text, String button1Text, String userName, String password) {
        return ConnectIdMessageFragmentDirections.actionConnectidMessageSelf(title, message, callingClass, button2Text, button1Text, userName, password);
    }

    private NavDirections navigateToPhoneVerify(int verifyType, int method, String primaryPhone, String userId, String password, String alternatePhone) {
        return ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(verifyType, String.format(Locale.getDefault(), "%d", method), primaryPhone, userId, password, alternatePhone, false);
    }

    private NavDirections navigateToPin(int pinType, String phone, String secret, boolean change, boolean recover) {
        return ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(pinType, phone, secret).setChange(change).setRecover(recover);
    }

    private NavDirections navigateToPassword(String phone, String secret, int callingClass) {
        return ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPassword(phone, secret, callingClass);
    }

    private NavDirections navigateToSecondaryPhoneFragment(int fragmentType) {
        return ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidSecondaryPhoneFragment(fragmentType);
    }

    private NavDirections navigateToUserDeactivateOtpVerify(String phone, String userName, String password) {
        return ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidUserDeactivateOtpVerify(phone, userName, password);
    }

    private void successFlow(Activity activity) {
        ConnectIDManager.getInstance().setStatus(ConnectIDManager.ConnectIdStatus.LoggedIn);
        ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
        activity.setResult(RESULT_OK);
        activity.finish();
    }
}
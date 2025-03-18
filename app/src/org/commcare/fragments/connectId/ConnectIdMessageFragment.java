package org.commcare.fragments.connectId;

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

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import static android.app.Activity.RESULT_OK;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectIdMessageFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectIdMessageFragment extends BottomSheetDialogFragment {
    private String title;
    private String message;
    private String buttonText;
    private String button2Text;
    private String userName;
    private String password;
    private int callingClass;
    private static final String KEY_TITLE = "title";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_BUTTON_TEXT = "button_text";
    private static final String KEY_BUTTON2_TEXT = "button2_text";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_CALLING_CLASS = "calling_class";
    private ScreenConnectMessageBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = ScreenConnectMessageBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        getLoadState(savedInstanceState);
        binding.connectMessageButton.setOnClickListener(v -> handleContinueButtonPress(false));
        binding.connectMessageButton2.setOnClickListener(v -> handleContinueButtonPress(true));
        loadArguments();
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

    private void getLoadState(Bundle savedInstanceState){
        title=savedInstanceState.getString(KEY_TITLE);
        message=savedInstanceState.getString(KEY_MESSAGE);
        buttonText=savedInstanceState.getString(KEY_BUTTON_TEXT);
        button2Text=savedInstanceState.getString(KEY_BUTTON2_TEXT);
        userName=savedInstanceState.getString(KEY_USER_NAME);
        password=savedInstanceState.getString(KEY_PASSWORD);
        callingClass=savedInstanceState.getInt(KEY_CALLING_CLASS);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_TITLE, title);
        outState.putString(KEY_MESSAGE, message);
        outState.putString(KEY_BUTTON_TEXT, buttonText);
        outState.putString(KEY_BUTTON2_TEXT, button2Text);
        outState.putString(KEY_USER_NAME, userName);
        outState.putString(KEY_PASSWORD, password);
        outState.putInt(KEY_CALLING_CLASS, callingClass);
    }

    private void loadArguments(){
        title = org.commcare.fragments.connectId.ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getTitle();
        message = org.commcare.fragments.connectId.ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getMessage();
        buttonText = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButtonText();
        callingClass = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getCallingClass();
        userName = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getPhone();
        password = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getPassword();
        if (ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text() != null && !ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text().isEmpty()) {
            button2Text = ConnectIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text();
        }
    }

    private void setButton2Text(String buttonText) {
        boolean show = buttonText != null;
        binding.connectMessageButton2.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.connectMessageButton2.setText(buttonText);
        }
    }

    private void handleContinueButtonPress(boolean secondButton) {
        finish(true, secondButton);
    }

    private void finish(boolean success, boolean secondButton) {
        NavDirections directions = null;
        ConnectIdActivity connectIdActivity= (ConnectIdActivity)requireActivity();
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getActivity());
        switch (callingClass) {
            case ConnectConstants.CONNECT_REGISTRATION_SUCCESS:
                if (success) {
                    successFlow();
                }
                break;
            //CONNECT_RECOVERY_ALT_PHONE_MESSAGE
            case ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE:
                if (success) {
                    if (secondButton) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageSelf(getString(R.string.connect_deactivate_account), getString(R.string.connect_deactivate_account_message), ConnectConstants.CONNECT_USER_DEACTIVATE_CONFIRMATION, getString(R.string.connect_deactivate_account_delete), getString(R.string.connect_deactivate_account_go_back), userName, password);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(
                                ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE,
                                String.format(Locale.getDefault(), "%d", ConnectIdPhoneVerificationFragment.MethodRecoveryAlternate), null,
                                connectIdActivity.recoverPhone,
                                connectIdActivity.recoverSecret,
                                ((ConnectIdActivity)getActivity()).recoveryAltPhone,
                                false
                        ).setAllowChange(false);
                    }
                }
                break;
            case ConnectConstants.CONNECT_RECOVERY_SUCCESS:
                successFlow();
                break;
            case ConnectConstants.CONNECT_BIOMETRIC_ENROLL_FAIL:
                if (success) {
                    SettingsHelper.launchSecuritySettings(requireActivity());
                } else {
                    directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidBiometricConfig(ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS);
                }
                break;
            case ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD:
                if (success) {
                    if (connectIdActivity.forgotPassword) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageSelf(getString(R.string.connect_recovery_alt_title), getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE, getString(R.string.connect_recovery_alt_button), null, userName, password);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_RECOVERY_CHANGE_PIN, connectIdActivity.recoverPhone, connectIdActivity.recoverSecret).setRecover(true).setChange(true);
                    }
                } else {
                    directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE, String.valueOf(ConnectIdPhoneVerificationFragment.MethodRecoveryPrimary), connectIdActivity.recoverPhone, connectIdActivity.recoverPhone, null, null,false).setAllowChange(false);
                }
                break;
            case ConnectConstants.CONNECT_RECOVERY_WRONG_PASSWORD:
                if (success) {
                    directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPassword(connectIdActivity.recoverPhone, connectIdActivity.recoverSecret, ConnectConstants.CONNECT_RECOVERY_VERIFY_PASSWORD);
                }
                break;
            case ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_MESSAGE:
                if (success) {
                    if (secondButton) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidSecondaryPhoneFragment(ConnectConstants.CONNECT_UNLOCK_ALT_PHONE_CHANGE, ConnectConstants.METHOD_CHANGE_ALTERNATE, null);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_UNLOCK_VERIFY_ALT_PHONE, String.valueOf(
                                ConnectIdPhoneVerificationFragment.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null,false).setAllowChange(false);
                    }
                }
                break;
            //CONNECT_RECOVERY_WRONG_PIN
            case ConnectConstants.CONNECT_RECOVERY_WRONG_PIN:
                if (success) {
                    if (ConnectIDManager.getInstance().getFailureAttempt() > 2) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE, String.valueOf(
                                ConnectIdPhoneVerificationFragment.MethodRecoveryAlternate), null, connectIdActivity.recoverPhone, connectIdActivity.recoverSecret, ((ConnectIdActivity)getActivity()).recoveryAltPhone,false).setAllowChange(false);
                        ConnectIDManager.getInstance().setFailureAttempt(0);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_RECOVERY_VERIFY_PIN, connectIdActivity.recoverPhone, connectIdActivity.recoverSecret).setChange(false).setRecover(true);
                    }
                }
                break;
            case ConnectConstants.CONNECT_REGISTRATION_WRONG_PIN:
                if (success) {
                    if (ConnectIDManager.getInstance().getFailureAttempt() > 2) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN, user.getPrimaryPhone(), "").setChange(true).setRecover(false);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPin(ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN, user.getPrimaryPhone(), "").setChange(false).setRecover(false);
                    }
                }
                break;
            case ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE:
                if (success) {
                    if (secondButton) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidSecondaryPhoneFragment(ConnectConstants.CONNECT_VERIFY_ALT_PHONE_CHANGE, ConnectConstants.METHOD_CHANGE_ALTERNATE, null);
                    } else {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidPhoneVerify(ConnectConstants.CONNECT_VERIFY_ALT_PHONE, String.valueOf(
                                ConnectIdPhoneVerificationFragment.MethodVerifyAlternate), null, user.getUserId(), user.getPassword(), null,false).setAllowChange(false);
                    }
                }
                break;
            case ConnectConstants.CONNECT_USER_DEACTIVATE_CONFIRMATION:
                if (success) {
                    if (!secondButton) {
                        directions = ConnectIdMessageFragmentDirections.actionConnectidMessageToConnectidUserDeactivateOtpVerify(
                                ConnectConstants.CONNECT_VERIFY_USER_DEACTIVATE,
                                String.valueOf(ConnectIdPhoneVerificationFragment.MethodUserDeactivate),
                                connectIdActivity.recoverPhone,
                                userName,
                                password,
                                null,true).setAllowChange(false);
                    } else {
                        NavHostFragment.findNavController(this).popBackStack();
                    }
                }
                break;
            case ConnectConstants.CONNECT_USER_DEACTIVATE_SUCCESS:
                if (success) {
                    if (!secondButton) {
                        ConnectIDManager.forgetUser("Account deactivation");
                        requireActivity().finish();
                    }
                }
                break;
        }
        if (success) {
            if (directions != null) {
                NavHostFragment.findNavController(this).navigate(directions);
            }
        }
    }

    private void successFlow(){
        ConnectIDManager.getInstance().setStatus(ConnectIDManager.ConnectIdStatus.LoggedIn);
        ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.CONNECT_NO_ACTIVITY);
        requireActivity().setResult(RESULT_OK);
        requireActivity().finish();
    }
}

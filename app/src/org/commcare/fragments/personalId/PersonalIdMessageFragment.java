package org.commcare.fragments.personalId;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.commcare.activities.SettingsHelper;
import org.commcare.activities.connect.PersonalIdActivity;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.dalvik.databinding.ScreenPersonalidMessageBinding;

import androidx.annotation.NonNull;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import static android.app.Activity.RESULT_OK;

public class PersonalIdMessageFragment extends BottomSheetDialogFragment {
    private String title;
    private String message;
    private String button2Text;
    private String userName;
    private String password;
    private boolean isCancellable = true;
    private int callingClass;
    private static final String KEY_TITLE = "title";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_BUTTON2_TEXT = "button2_text";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_CALLING_CLASS = "calling_class";
    private static final String KEY_IS_CANCELLABLE = "is_cancellable";
    private ScreenPersonalidMessageBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = ScreenPersonalidMessageBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        loadSavedState(savedInstanceState);
        binding.connectMessageButton.setOnClickListener(v -> handleContinueButtonPress());
        binding.connectMessageButton2.setOnClickListener(v -> handleContinueButtonPress());
        loadArguments();
        this.setCancelable(isCancellable);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.connectMessageTitle.setText(title);
        binding.connectMessageMessage.setText(message);
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
            isCancellable = savedInstanceState.getBoolean(KEY_IS_CANCELLABLE);
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
        outState.putBoolean(KEY_IS_CANCELLABLE, isCancellable);
    }

    private void loadArguments() {
        title = PersonalIdMessageFragmentArgs.fromBundle(getArguments()).getTitle();
        message = PersonalIdMessageFragmentArgs.fromBundle(getArguments()).getMessage();
        callingClass = PersonalIdMessageFragmentArgs.fromBundle(getArguments()).getCallingClass();
        userName = PersonalIdMessageFragmentArgs.fromBundle(getArguments()).getPhone();
        password = PersonalIdMessageFragmentArgs.fromBundle(getArguments()).getPassword();
        isCancellable = PersonalIdMessageFragmentArgs.fromBundle(getArguments()).getIsCancellable();
        if (PersonalIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text() != null && !PersonalIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text().isEmpty()) {
            button2Text = PersonalIdMessageFragmentArgs.fromBundle(getArguments()).getButton2Text();
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

    private void handleContinueButtonPress() {
        finish();
    }

    private void finish() {
        NavDirections directions = null;
        Activity activity = requireActivity();
        PersonalIdActivity personalIdActivity = (PersonalIdActivity)activity;
        switch (callingClass) {
            case ConnectConstants.PERSONALID_REGISTRATION_SUCCESS, ConnectConstants.PERSONALID_RECOVERY_SUCCESS:
                successFlow(activity);
                break;
            case ConnectConstants.PERSONALID_BIOMETRIC_ENROLL_FAIL:
                SettingsHelper.launchSecuritySettings(activity);
                break;
            case ConnectConstants.PERSONALID_RECOVERY_WRONG_PIN:
                if (PersonalIdManager.getInstance().getFailureAttempt() > 2) {
                    directions = navigateToPhoneVerify(personalIdActivity.primaryPhone);
                    PersonalIdManager.getInstance().setFailureAttempt(0);
                } else {
                    directions = navigateToBackupCode();
                }

                break;
            case ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED:
                directions = navigateToPhoneFragment();
                break;

        }
        if (directions != null) {
            NavHostFragment.findNavController(this).navigate(directions);

        }
    }

    private NavDirections navigateToMessage(String title, String message, int callingClass, String button2Text,
                                            String button1Text, String userName, String password) {
        return PersonalIdMessageFragmentDirections.actionPersonalidMessageSelf(title, message, callingClass,
                button2Text, button1Text, userName, password);
    }

    private NavDirections navigateToPhoneVerify(String primaryPhone) {
        return PersonalIdMessageFragmentDirections.actionPersonalidMessageToPersonalidPhoneVerify(primaryPhone);
    }

    private NavDirections navigateToBackupCode() {
        return PersonalIdMessageFragmentDirections.actionPersonalidMessageToPersonalidPin("", "");
    }

    private NavDirections navigateToPhoneFragment() {
        return PersonalIdMessageFragmentDirections.actionPersonalidMessageDisplayToPersonalidPhoneFragment();
    }


    private void successFlow(Activity activity) {
        PersonalIdManager.getInstance().setStatus(PersonalIdManager.PersonalIdStatus.LoggedIn);
        ConnectDatabaseHelper.setRegistrationPhase(getActivity(), ConnectConstants.PERSONALID_NO_ACTIVITY);
        activity.setResult(RESULT_OK);
        activity.finish();
    }
}

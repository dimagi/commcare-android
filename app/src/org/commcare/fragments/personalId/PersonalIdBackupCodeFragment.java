package org.commcare.fragments.personalId;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.network.PersonalIdApiErrorHandler;
import org.commcare.connect.network.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentRecoveryCodeBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.MediaUtil;
import java.util.Date;

public class PersonalIdBackupCodeFragment extends Fragment {
    private static final int BACKUP_CODE_LENGTH = 6;
    private FragmentRecoveryCodeBinding binding;
    private Activity activity;
    private String secret;
    private boolean isRecovery = false;
    private int titleId;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;
    private PersonalIdSessionData personalIdSessionData;

    @Override
    public void onResume() {
        super.onResume();
        KeyboardHelper.showKeyboardOnInput(activity, binding.connectPinInput);
        validateBackupCodeInputs();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRecoveryCodeBinding.inflate(inflater, container, false);
        activity = requireActivity();
        personalIdSessionDataViewModel = new ViewModelProvider(requireActivity()).get(
                PersonalIdSessionDataViewModel.class);
        personalIdSessionData = personalIdSessionDataViewModel.getPersonalIdSessionData();
        isRecovery = personalIdSessionData.getAccountExists();
        configureUiByMode();
        setupInputFilters();
        setupListeners();
        clearBackupCodeFields();
        activity.setTitle(getString(titleId));
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void configureUiByMode() {
        if (isRecovery) {
            titleId = R.string.connect_pin_title_confirm;
            binding.confirmCodeLayout.setVisibility(View.GONE);
            binding.recoveryCodeTilte.setText(R.string.connect_pin_message_title);
            binding.phoneTitle.setText(R.string.connect_pin_message);
            binding.nameLayout.setVisibility(View.VISIBLE);
            binding.notMeButton.setVisibility(View.VISIBLE);
            setUserNameAndPhoto();
        } else {
            titleId = R.string.connect_pin_title_set;
            binding.confirmCodeLayout.setVisibility(View.VISIBLE);
            binding.notMeButton.setVisibility(View.GONE);
        }
    }

    private void setUserNameAndPhoto() {
        binding.welcomeBack.setText(getString(R.string.welcome_back_msg, personalIdSessionData.getUserName()));
        if (!TextUtils.isEmpty(personalIdSessionData.getPhotoBase64())) {
            binding.userPhoto.setImageBitmap(MediaUtil.decodeBase64EncodedBitmap(personalIdSessionData.getPhotoBase64()));
        }
    }

    private void setupInputFilters() {
        InputFilter[] filters = new InputFilter[]{new InputFilter.LengthFilter(BACKUP_CODE_LENGTH)};
        binding.connectPinInput.setFilters(filters);
        binding.connectPinRepeatInput.setFilters(filters);
    }

    private void setupListeners() {
        TextWatcher pinWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateBackupCodeInputs();
            }
        };

        binding.connectPinInput.addTextChangedListener(pinWatcher);
        binding.connectPinRepeatInput.addTextChangedListener(pinWatcher);
        binding.connectPinButton.setOnClickListener(v -> handleBackupCodeSubmission());
    }

    private void clearBackupCodeFields() {
        binding.connectPinInput.setText("");
        binding.connectPinRepeatInput.setText("");
    }

    private void validateBackupCodeInputs() {
        String code1 = String.valueOf(binding.connectPinInput.getText());
        String code2 = String.valueOf(binding.connectPinRepeatInput.getText());

        boolean isValid = !code1.isEmpty() && code1.length() == BACKUP_CODE_LENGTH &&
                (isRecovery || code1.equals(code2));

        String errorText = !isValid && code1.length() != BACKUP_CODE_LENGTH
                ? getString(R.string.connect_pin_length, BACKUP_CODE_LENGTH)
                : (!isRecovery && !code1.equals(code2))
                        ? getString(R.string.connect_pin_mismatch)
                        : "";

        binding.connectPinErrorMessage.setText(errorText);
        binding.connectPinButton.setEnabled(isValid);
    }

    private void handleBackupCodeSubmission() {
        if (isRecovery) {
            confirmBackupCode();
        } else {
            Navigation.findNavController(binding.getRoot()).navigate(createNavigationToPhoto());
        }
    }

    private void confirmBackupCode() {
        String backupCode = binding.connectPinInput.getText().toString();

        new PersonalIdApiHandler() {
            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                if (Boolean.FALSE.equals(sessionData.getAccountOrphaned())) {
                    handleSuccessfulRecovery();
                } else {
                    navigateWithMessage(R.string.recovery_failed, R.string.recovery_failed_message,
                            ConnectConstants.PERSONALID_RECOVERY_WRONG_PIN);
                }
            }

            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode) {
                if (failureCode == PersonalIdApiErrorCodes.WRONG_BACKUP_CODE) {
                    handleFailedBackupCodeAttempt();
                } else {
                    PersonalIdApiErrorHandler.handle(requireActivity(), failureCode);
                }
            }
        }.confirmBackupCode(activity, backupCode, personalIdSessionData);
    }

    private void handleSuccessfulRecovery() {
        ConnectDatabaseHelper.handleReceivedDbPassphrase(activity, personalIdSessionData.getDbKey());
        ConnectUserRecord user = new ConnectUserRecord(personalIdSessionData.getPhoneNumber(), personalIdSessionData.getUserId(),
                personalIdSessionData.getOauthPassword(), personalIdSessionData.getUserName());
        user.setPin(binding.connectPinInput.getText().toString());
        user.setLastPinDate(new Date());
        logRecoveryResult(true);

        Navigation.findNavController(binding.getRoot()).navigate(createSuccessRecoveryDirection());
    }

    private void handleFailedBackupCodeAttempt() {
        PersonalIdManager idManager = PersonalIdManager.getInstance();
        idManager.setFailureAttempt(idManager.getFailureAttempt() + 1);
        logRecoveryResult(false);
        clearBackupCodeFields();
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PIN);
    }

    private void navigateWithMessage(int titleRes, int msgRes, int phase) {
        Navigation.findNavController(binding.getRoot())
                .navigate(createNavigationMessage(getString(titleRes), getString(msgRes), phase,
                        getString(R.string.ok)));
    }

    private NavDirections createNavigationToPhoto() {
        return PersonalIdBackupCodeFragmentDirections
                .actionPersonalidPinToPersonalidPhotoCapture(
                        personalIdSessionData.getUserName(),
                        binding.connectPinInput.getText().toString());
    }

    private NavDirections createSuccessRecoveryDirection() {
        return createNavigationMessage(
                getString(R.string.connect_recovery_success_title),
                getString(R.string.connect_recovery_success_message),
                ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
                getString(R.string.connect_recovery_success_button));
    }

    private NavDirections createNavigationMessage(String title, String message, int phase, String buttonText) {
        return PersonalIdBackupCodeFragmentDirections
                .actionPersonalidPinToPersonalidMessage(title, message, phase, buttonText, null,
                        personalIdSessionData.getUserName(), secret)
                .setIsCancellable(false);
    }
}
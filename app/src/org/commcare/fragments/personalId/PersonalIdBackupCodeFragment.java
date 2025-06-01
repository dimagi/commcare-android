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
import androidx.navigation.Navigation;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
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
    private boolean isRecovery = false;
    private int titleId;
    private PersonalIdSessionData personalIdSessionData;

    @Override
    public void onResume() {
        super.onResume();
        KeyboardHelper.showKeyboardOnInput(activity, binding.connectBackupCodeInput);
        validateBackupCodeInputs();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRecoveryCodeBinding.inflate(inflater, container, false);
        activity = requireActivity();
        personalIdSessionData = new ViewModelProvider(requireActivity()).get(
                PersonalIdSessionDataViewModel.class).getPersonalIdSessionData();
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
            titleId = R.string.connect_backup_code_title_confirm;
            binding.confirmCodeLayout.setVisibility(View.GONE);
            binding.recoveryCodeTilte.setText(R.string.connect_backup_code_message_title);
            binding.backupCodeSubtitle.setText(R.string.connect_backup_code_message);
            binding.nameLayout.setVisibility(View.VISIBLE);
            binding.notMeButton.setVisibility(View.VISIBLE);
            setUserNameAndPhoto();
        } else {
            titleId = R.string.connect_backup_code_title_set;
            binding.confirmCodeLayout.setVisibility(View.VISIBLE);
            binding.notMeButton.setVisibility(View.GONE);
            binding.nameLayout.setVisibility(View.GONE);
        }
    }

    private void setUserNameAndPhoto() {
        binding.welcomeBack.setText(getString(R.string.welcome_back_msg, personalIdSessionData.getUserName()));
        if (!TextUtils.isEmpty(personalIdSessionData.getPhotoBase64())) {
            binding.userPhoto.setImageBitmap(
                    MediaUtil.decodeBase64EncodedBitmap(personalIdSessionData.getPhotoBase64()));
        }
    }

    private void setupInputFilters() {
        InputFilter[] filters = new InputFilter[]{new InputFilter.LengthFilter(BACKUP_CODE_LENGTH)};
        binding.connectBackupCodeInput.setFilters(filters);
        binding.connectBackupCodeRepeatInput.setFilters(filters);
    }

    private void setupListeners() {
        TextWatcher backupCodeWatcher = new TextWatcher() {
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

        binding.connectBackupCodeInput.addTextChangedListener(backupCodeWatcher);
        binding.connectBackupCodeRepeatInput.addTextChangedListener(backupCodeWatcher);
        binding.connectBackupCodeButton.setOnClickListener(v -> handleBackupCodeSubmission());
    }

    private void clearBackupCodeFields() {
        binding.connectBackupCodeInput.setText("");
        binding.connectBackupCodeRepeatInput.setText("");
    }

    private void validateBackupCodeInputs() {
        String backupCode1 = binding.connectBackupCodeInput.getText().toString();
        String backupCode2 = binding.connectBackupCodeRepeatInput.getText().toString();

        String errorText = "";
        boolean isValid = false;

        if (!backupCode1.isEmpty()) {
            if (backupCode1.length() != BACKUP_CODE_LENGTH) {
                errorText = getString(R.string.connect_backup_code_length, BACKUP_CODE_LENGTH);
            } else if (!isRecovery && !backupCode1.equals(backupCode2)) {
                errorText = getString(R.string.connect_backup_code_mismatch);
            } else {
                isValid = true;
            }
        }

        binding.connectBackupCodeErrorMessage.setText(errorText);
        enableContinueButton(isValid);
    }

    private void enableContinueButton(boolean isEnable) {
        binding.connectBackupCodeButton.setEnabled(isEnable);
    }

    private void handleBackupCodeSubmission() {
        if (isRecovery) {
            confirmBackupCode();
        } else {
            personalIdSessionData.setBackupCode(
                    binding.connectBackupCodeInput.getText().toString());
            navigateToPhoto();
        }
    }

    private void confirmBackupCode() {
        enableContinueButton(false);
        String backupCode = binding.connectBackupCodeInput.getText().toString();

        new PersonalIdApiHandler() {
            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                if (sessionData.getDbKey() != null) {
                    handleSuccessfulRecovery();
                } else if (sessionData.getAttemptsLeft() != null && sessionData.getAttemptsLeft() == 0) {
                    navigateWithMessage(getString(R.string.recovery_failed_title),
                            getString(R.string.recovery_failed_message),
                            ConnectConstants.PERSONALID_RECOVERY_ACCOUNT_ORPHANED);
                } else if (sessionData.getAttemptsLeft() != null && sessionData.getAttemptsLeft() > 0) {
                    handleFailedBackupCodeAttempt();
                }
            }

            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode) {
                PersonalIdApiErrorHandler.handle(requireActivity(), failureCode);

                if (failureCode.shouldAllowRetry()) {
                    enableContinueButton(true);
                }
            }
        }.confirmBackupCode(activity, backupCode, personalIdSessionData);
    }

    private void handleSuccessfulRecovery() {
        ConnectDatabaseHelper.handleReceivedDbPassphrase(activity, personalIdSessionData.getDbKey());
        ConnectUserRecord user = new ConnectUserRecord(personalIdSessionData.getPhoneNumber(),
                personalIdSessionData.getPersonalId(),
                personalIdSessionData.getOauthPassword(), personalIdSessionData.getUserName(),
                String.valueOf(binding.connectBackupCodeInput.getText()), new Date(),
                personalIdSessionData.getPhotoBase64(),
                personalIdSessionData.getDemoUser());
        ConnectUserDatabaseUtil.storeUser(requireActivity(), user);
        logRecoveryResult(true);
        navigateToSuccess();
    }

    private void handleFailedBackupCodeAttempt() {
        logRecoveryResult(false);
        clearBackupCodeFields();
        navigateWithMessage(getString(R.string.connect_backup_fail_title),
                getString(R.string.personalid_wrong_backup_message, personalIdSessionData.getAttemptsLeft()),
                ConnectConstants.PERSONALID_RECOVERY_WRONG_BACKUPCODE);
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_BACKUPCODE);
    }

    private void navigateWithMessage(String titleRes, String msgRes, int phase) {
        Navigation.findNavController(binding.getRoot())
                .navigate(PersonalIdBackupCodeFragmentDirections
                        .actionPersonalidBackupcodeToPersonalidMessage(titleRes, msgRes, phase,
                                getString(R.string.ok), null)
                        .setIsCancellable(false));
    }

    private void navigateToPhoto() {
        Navigation.findNavController(binding.getRoot())
                .navigate(PersonalIdBackupCodeFragmentDirections
                        .actionPersonalidBackupcodeToPersonalidPhotoCapture());
    }

    private void navigateToSuccess() {
        navigateWithMessage(
                getString(R.string.connect_recovery_success_title),
                getString(R.string.connect_recovery_success_message),
                ConnectConstants.PERSONALID_RECOVERY_SUCCESS);
    }
}
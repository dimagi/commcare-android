package org.commcare.fragments.personalId;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.CommCareNoficationManager;
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler;
import org.commcare.connect.network.connectId.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentRecoveryCodeBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.NotificationUtil;
import org.commcare.views.connect.CustomOtpView;
import org.javarosa.core.model.utils.DateUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public class PersonalIdBackupCodeFragment extends BasePersonalIdFragment {
    private static final int BACKUP_CODE_LENGTH = 6; //Note: This is brittle, defined in several places
    private FragmentRecoveryCodeBinding binding;
    private Activity activity;
    private boolean isRecovery = false;
    private int titleId;
    private PersonalIdSessionData personalIdSessionData;

    @Override
    public void onResume() {
        super.onResume();
        validateBackupCodeInputs();
        binding.backupCodeView.requestFocus(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRecoveryCodeBinding.inflate(inflater, container, false);
        activity = requireActivity();
        personalIdSessionData = new ViewModelProvider(requireActivity()).get(
                PersonalIdSessionDataViewModel.class).getPersonalIdSessionData();
        configureUiByMode();
        setupListeners();
        clearBackupCodeFields();
        activity.setTitle(getString(titleId));
        return binding.getRoot();
    }

    private void configureUiByMode() {
        isRecovery = personalIdSessionData.getAccountExists();
        if (isRecovery) {
            titleId = R.string.connect_backup_code_title_confirm;
            binding.recoveryCodeTilte.setText(R.string.connect_backup_code_message_title);
            binding.backupCodeSubtitle.setText(R.string.connect_backup_code_message);
            binding.backupCodeLayout.setVisibility(View.VISIBLE);
            binding.confirmCodeLabel.setVisibility(View.GONE);
            binding.confirmCodeLayout.setVisibility(View.GONE);
            setUserNameAndPhoto();
        } else {
            titleId = R.string.connect_backup_code_title_set;
            binding.backupCodeSubtitle.setText(getString(R.string.connect_backup_code_remember, BACKUP_CODE_LENGTH));
            binding.backupCodeLayout.setVisibility(View.VISIBLE);
            binding.confirmCodeLabel.setVisibility(View.VISIBLE);
            binding.confirmCodeLayout.setVisibility(View.VISIBLE);
            binding.welcomeBackLayout.setVisibility(View.GONE);
        }
    }

    private void setUserNameAndPhoto() {
        binding.welcomeBack.setText(getString(R.string.personalid_welcome_back_msg, personalIdSessionData.getUserName()));
        if (!TextUtils.isEmpty(personalIdSessionData.getPhotoBase64())) {
            binding.userPhoto.setImageBitmap(
                    MediaUtil.decodeBase64EncodedBitmap(personalIdSessionData.getPhotoBase64()));
        }
    }

    private void setupListeners() {
        CustomOtpView.OnOtpChangedListener codeChangedListener = code -> validateBackupCodeInputs();

        binding.backupCodeView.setOnOtpChangedListener(codeChangedListener);
        binding.confirmCodeView.setOnOtpChangedListener(codeChangedListener);

        binding.backupCodeView.setOtpCompleteListener(code -> {
            if (isRecovery && binding.connectBackupCodeButton.isEnabled()) {
                handleBackupCodeSubmission();
            }
        });

        binding.confirmCodeView.setOtpCompleteListener(code -> {
            if (binding.connectBackupCodeButton.isEnabled()) {
                handleBackupCodeSubmission();
            }
        });

        binding.connectBackupCodeButton.setOnClickListener(v -> handleBackupCodeSubmission());
        binding.notMeButton.setOnClickListener(v -> handleNotMeButtonPressed());

        binding.backupCodeVisibilityToggle.setOnClickListener(v -> togglePasswordVisibility(
                binding.backupCodeView, binding.backupCodeVisibilityToggle));
        binding.confirmCodeVisibilityToggle.setOnClickListener(v -> togglePasswordVisibility(
                binding.confirmCodeView, binding.confirmCodeVisibilityToggle));
    }

    private void togglePasswordVisibility(CustomOtpView codeView, ImageView toggle) {
        boolean newVisibilityState = !codeView.isPasswordVisible();
        codeView.setPasswordVisible(newVisibilityState);
        toggle.setImageResource(newVisibilityState
                ? R.drawable.ic_visibility_off_24
                : R.drawable.ic_visibility_24);
    }

    private void clearBackupCodeFields() {
        binding.confirmCodeView.clearOtp();
        binding.backupCodeView.clearOtp();
    }

    private void validateBackupCodeInputs() {
        String backupCode1 = binding.backupCodeView.getOtpValue();
        String backupCode2 = binding.confirmCodeView.getOtpValue();

        String errorText = "";
        boolean isValid = false;

        if (backupCode1.length() == BACKUP_CODE_LENGTH) {
            if (!isRecovery && !backupCode1.equals(backupCode2)) {
                if (backupCode2.length() == BACKUP_CODE_LENGTH) {
                    errorText = getString(R.string.connect_backup_code_mismatch);
                }
            } else {
                isValid = true;
            }
        }

        binding.connectBackupCodeErrorMessage.setVisibility(TextUtils.isEmpty(errorText) ? View.GONE : View.VISIBLE);
        binding.connectBackupCodeErrorMessage.setText(errorText);
        enableContinueButton(isValid);
    }

    private void handleNotMeButtonPressed(){
        personalIdSessionData.setAccountExists(false);
        clearBackupCodeFields();
        configureUiByMode();
    }

    private void enableContinueButton(boolean isEnable) {
        binding.connectBackupCodeButton.setEnabled(isEnable);
    }

    private void handleBackupCodeSubmission() {
        FirebaseAnalyticsUtil.reportPersonalIDContinueClicked(this.getClass().getSimpleName(),null);
        if (isRecovery) {
            confirmBackupCode();
        } else {
            personalIdSessionData.setBackupCode(
                    binding.backupCodeView.getOtpValue());
            navigateToPhoto();
        }
    }

    private void confirmBackupCode() {
        clearError();
        enableContinueButton(false);
        String backupCode = binding.backupCodeView.getOtpValue();

        new PersonalIdApiHandler<PersonalIdSessionData>() {
            @Override
            public void onSuccess(PersonalIdSessionData sessionData) {
                if (sessionData.getDbKey() != null) {
                    handleSuccessfulRecovery();
                } else if (sessionData.getAttemptsLeft() != null && sessionData.getAttemptsLeft() > 0) {
                    handleFailedBackupCodeAttempt();
                }
            }

            @Override
            public void onFailure(PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
                if (handleCommonSignupFailures(failureCode)) {
                    return;
                }
                showError(PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t));
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
                binding.backupCodeView.getOtpValue(), new Date(),
                personalIdSessionData.getPhotoBase64(),
                personalIdSessionData.getDemoUser(),personalIdSessionData.getRequiredLock(),
                personalIdSessionData.getInvitedUser());
        ConnectUserDatabaseUtil.storeUser(requireActivity(), user);
        logRecoveryResult(true);
        handleSecondDeviceLogin();
        navigateToSuccess();
    }

    private void clearError() {
        binding.connectBackupCodeErrorMessage.setVisibility(View.GONE);
        binding.connectBackupCodeErrorMessage.setText("");
    }

    private void showError(String message) {
        binding.connectBackupCodeErrorMessage.setVisibility(View.VISIBLE);
        binding.connectBackupCodeErrorMessage.setText(message);
    }

    private void handleFailedBackupCodeAttempt() {
        logRecoveryResult(false);
        clearBackupCodeFields();
        navigateWithMessage(getString(R.string.connect_backup_fail_title),
                getString(R.string.personalid_wrong_backup_message, personalIdSessionData.getAttemptsLeft()),
                ConnectConstants.PERSONALID_RECOVERY_WRONG_BACKUPCODE);
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportPersonalIdAccountRecovered(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_BACKUPCODE);
    }

    private void handleSecondDeviceLogin() {
        if(personalIdSessionData.getPreviousDevice() != null) {
            int titleId = R.string.personalid_second_device_login_title;
            String message;
            if (personalIdSessionData.getLastAccessed() != null) {
                message = getString(R.string.personalid_second_device_login_message,
                        personalIdSessionData.getPreviousDevice(),
                        DateUtils.getShortStringValue(personalIdSessionData.getLastAccessed()));
            } else {
                message = getString(R.string.personalid_second_device_login_message_no_date,
                        personalIdSessionData.getPreviousDevice());
            }

            NotificationUtil.showNotification(requireContext(),
                    CommCareNoficationManager.NOTIFICATION_CHANNEL_SERVER_COMMUNICATIONS_ID,
                    titleId,
                    getString(titleId),
                    message,
                    null);
        }
    }

    private void navigateWithMessage(String titleRes, String msgRes, int phase) {
        navigateToMessageDisplay(titleRes, msgRes, false, phase, R.string.ok);
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

    @Override
    protected void navigateToMessageDisplay(@NotNull String title, @Nullable String message, boolean isCancellable,
            int phase, int buttonText) {
        NavDirections navDirections = PersonalIdBackupCodeFragmentDirections
                .actionPersonalidBackupcodeToPersonalidMessage(title, message, phase,
                        getString(buttonText), null)
                .setIsCancellable(isCancellable);
        Navigation.findNavController(binding.getRoot()).navigate(navDirections);
    }
}

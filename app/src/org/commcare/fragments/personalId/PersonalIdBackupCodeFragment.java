package org.commcare.fragments.personalId;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.util.Base64;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiPersonalId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.connect.network.PersonalIdApiErrorHandler;
import org.commcare.connect.network.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentRecoveryCodeBinding;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.suite.model.SessionDatum;
import org.commcare.utils.KeyboardHelper;
import org.commcare.utils.MediaUtil;
import org.javarosa.core.services.Logger;

import java.io.InputStream;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PersonalIdBackupCodeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PersonalIdBackupCodeFragment extends Fragment {
    private static final int BACKUP_CODE_LENGTH = 6;
    private FragmentRecoveryCodeBinding binding;
    private Activity activity;

    private String name = null;
    private String secret = null;
    private boolean isRecovery;
    private int titleId;
    private PersonalIdSessionDataViewModel personalIdSessionDataViewModel;


    @Override
    public void onResume() {
        super.onResume();
        requestInputFocus();
        validateBackupCodeInputs();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRecoveryCodeBinding.inflate(inflater, container, false);
        activity = requireActivity();
        configureUiByMode();
        setupBackupCodeInputFilters();
        setupListeners();
        clearBackupCodeFields();
        personalIdSessionDataViewModel = new ViewModelProvider(requireActivity()).get(PersonalIdSessionDataViewModel.class);
        activity.setTitle(getString(titleId));
        return binding.getRoot();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ─────────── UI SETUP ─────────────

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
        String username = personalIdSessionDataViewModel.getPersonalIdSessionData().getUsername();
        String photoBase64 = personalIdSessionDataViewModel.getPersonalIdSessionData().getPhotoBase64();
        binding.welcomeBack.setText(getString(R.string.welcome_back_msg, username));
        if (!TextUtils.isEmpty(photoBase64)) {
            binding.userPhoto.setImageBitmap(MediaUtil.decodeBase64EncodedBitmap(photoBase64));
        }
    }

    private void setupBackupCodeInputFilters() {
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
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateBackupCodeInputs();
            }

            @Override
            public void afterTextChanged(Editable s) {
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

    private void requestInputFocus() {
        KeyboardHelper.showKeyboardOnInput(activity, binding.connectPinInput);
    }

    // ─────────── VALIDATION ─────────────

    private void validateBackupCodeInputs() {
        String backupCode1 = binding.connectPinInput.getText().toString();
        String backupCode2 = binding.connectPinRepeatInput.getText().toString();

        String errorText = "";
        boolean isButtonEnabled = false;

        if (!backupCode1.isEmpty()) {
            if (backupCode1.length() != BACKUP_CODE_LENGTH) {
                errorText = getString(R.string.connect_pin_length, BACKUP_CODE_LENGTH);
            } else if (!isRecovery && !backupCode1.equals(backupCode2)) {
                errorText = getString(R.string.connect_pin_mismatch);
            } else {
                isButtonEnabled = true;
            }
        }

        binding.connectPinErrorMessage.setText(errorText);
        binding.connectPinButton.setEnabled(isButtonEnabled);
    }

    // ─────────── PIN HANDLING ─────────────

    private void handleBackupCodeSubmission() {
        if (isRecovery) {
            confirmBackupCode();
        } else {
            registerBackupCode();
        }
    }

    private void registerBackupCode() {
        Navigation.findNavController(binding.getRoot()).navigate(createNavigationToPhoto());
    }

    private void confirmBackupCode() {
        String backupCode = binding.connectPinInput.getText().toString();

        new PersonalIdApiHandler() {
            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                if(!sessionData.getAccountOrphaned()){
                    handleSuccessfulRecovery();
                }else{
                    Navigation.findNavController(binding.getRoot()).navigate(
                            createNavigationMessage(getString(R.string.recovery_failed),
                                    getString(R.string.recovery_failed_message),
                                    ConnectConstants.PERSONALID_RECOVERY_WRONG_PIN, getString(R.string.ok)));
                }
            }

            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode) {
                navigateFailure(failureCode);
            }
        }.confirmBackupCode(
                requireActivity(),
                backupCode,
                personalIdSessionDataViewModel.getPersonalIdSessionData());

    }

    private void navigateFailure(PersonalIdApiHandler.PersonalIdApiErrorCodes failureCode) {
        PersonalIdApiErrorHandler.handle(requireActivity(), failureCode);
    }

    private void handleSuccessfulRecovery() {
        PersonalIdSessionData personalIdSessionData=personalIdSessionDataViewModel.getPersonalIdSessionData();

        ConnectDatabaseHelper.handleReceivedDbPassphrase(activity,
                personalIdSessionData.getDbKey());
        ConnectUserRecord user = new ConnectUserRecord(name, personalIdSessionData.getUsername(), "", name, "");
        user.setPin(binding.connectPinInput.getText().toString());
        user.setLastPinDate(new Date());
        resetUserPassword(user);
        Navigation.findNavController(binding.getRoot()).navigate(createSuccessRecoveryDirection());
    }

    private void resetUserPassword(ConnectUserRecord user) {
        String password = PersonalIdManager.getInstance().generatePassword();
        ApiPersonalId.resetPassword(activity, name, secret, password, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                user.setPassword(password);
                ConnectUserDatabaseUtil.storeUser(activity, user);
            }

            @Override
            public void processFailure(int responseCode) {
                showRecoveryFailure();
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(getActivity());
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(requireActivity());
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException(requireActivity());
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(getActivity());
            }
        });
    }

    // ─────────── HELPERS ─────────────

    private void handleFailedBackupCodeAttempt() {
        PersonalIdManager idManager = PersonalIdManager.getInstance();
        idManager.setFailureAttempt(idManager.getFailureAttempt() + 1);
        logRecoveryResult(false);
        clearBackupCodeFields();
    }

    private void showRecoveryFailure() {
        Toast.makeText(activity, getString(R.string.connect_recovery_failure), Toast.LENGTH_SHORT).show();
    }

    private NavDirections createSuccessRecoveryDirection() {
        return createNavigationMessage(
                getString(R.string.connect_recovery_success_title),
                getString(R.string.connect_recovery_success_message),
                ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
                getString(R.string.connect_recovery_success_button)
        );
    }


    private NavDirections createNavigationMessage(String title, String message, int phase, String buttonText) {
        return PersonalIdBackupCodeFragmentDirections
                .actionPersonalidPinToPersonalidMessage(title, message, phase, buttonText, null, name, secret)
                .setIsCancellable(false);
    }

    private NavDirections createNavigationToPhoto() {
        return PersonalIdBackupCodeFragmentDirections
                .actionPersonalidPinToPersonalidPhotoCapture(name,
                        String.valueOf(binding.connectPinInput.getText()));
    }

    private void logRecoveryResult(boolean success) {
        FirebaseAnalyticsUtil.reportCccRecovery(success, AnalyticsParamValue.CCC_RECOVERY_METHOD_PIN);
    }
}

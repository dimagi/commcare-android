package org.commcare.fragments.personalId;

import static org.commcare.fragments.MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA;
import static org.commcare.fragments.MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler;
import org.commcare.connect.network.connectId.PersonalIdApiHandler;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidPhotoCaptureBinding;
import org.commcare.fragments.MicroImageActivity;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.MediaUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

/**
 * Screen to capture user's photo as part of Connect ID Account management process
 */
public class PersonalIdPhotoCaptureFragment extends BasePersonalIdFragment {

    private static final int PHOTO_MAX_DIMENSION_PX = 160;
    private static final int PHOTO_MAX_SIZE_BYTES = 100 * 1024; // 100 KB
    private ActivityResultLauncher<Intent> takePhotoLauncher;
    private @NonNull ScreenPersonalidPhotoCaptureBinding viewBinding;
    private PersonalIdSessionData personalIdSessionData;
    private String photoAsBase64;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewBinding = ScreenPersonalidPhotoCaptureBinding.inflate(inflater, container, false);
        personalIdSessionData = new ViewModelProvider(requireActivity()).get(
                PersonalIdSessionDataViewModel.class).getPersonalIdSessionData();
        initTakePhotoLauncher();
        setUpUi();
        return viewBinding.getRoot();
    }

    private void initTakePhotoLauncher() {
        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        photoAsBase64 = result.getData().getStringExtra(
                                MicroImageActivity.MICRO_IMAGE_BASE_64_RESULT_KEY);
                        displayImage(photoAsBase64);
                        enableSaveButton();
                    }
                    enableTakePhotoButton();
                }
        );
    }

    private void setUpUi() {
        requireActivity().setTitle(getString(R.string.personalid_capture_photo));
        viewBinding.title.setText(getString(R.string.personalid_photo_capture_title, personalIdSessionData.getUserName()));
        viewBinding.takePhotoButton.setOnClickListener(v -> executeTakePhoto());
        viewBinding.savePhotoButton.setOnClickListener(v -> uploadImageAndCompleteProfile());
    }

    private void uploadImageAndCompleteProfile() {
        clearError();
        disableSaveButton();
        disableTakePhotoButton();
        new PersonalIdApiHandler<PersonalIdSessionData>() {
            @Override
            public void onSuccess(PersonalIdSessionData sessionData) {
                onPhotoUploadSuccess(photoAsBase64);
            }

            @Override
            public void onFailure(PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
                onCompleteProfileFailure(failureCode, t);
            }
        }.completeProfile(requireContext(), personalIdSessionData.getUserName(),
                photoAsBase64,
                personalIdSessionData.getBackupCode(), personalIdSessionData.getToken(), personalIdSessionData);
    }

    private void onCompleteProfileFailure(PersonalIdApiHandler.PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
        if (handleCommonSignupFailures(failureCode)) {
            return;
        }

        showError(PersonalIdApiErrorHandler.handle(requireActivity(), failureCode, t));

        if (failureCode.shouldAllowRetry()) {
            enableTakePhotoButton();
            enableSaveButton();
        }
    }

    private void enableSaveButton() {
        viewBinding.savePhotoButton.setEnabled(true);
    }

    private void disableSaveButton() {
        viewBinding.savePhotoButton.setEnabled(false);
    }

    private void onPhotoUploadSuccess(String photoAsBase64) {
        enableTakePhotoButton();
        disableSaveButton();
        createAndSaveConnectUser(photoAsBase64);
        logAndShowAccountComplete();
    }

    private void createAndSaveConnectUser(String photoAsBase64) {
        ConnectDatabaseHelper.handleReceivedDbPassphrase(requireActivity(), personalIdSessionData.getDbKey());
        ConnectUserRecord user = new ConnectUserRecord(personalIdSessionData.getPhoneNumber(),
                personalIdSessionData.getPersonalId(),
                personalIdSessionData.getOauthPassword(), personalIdSessionData.getUserName(),
                String.valueOf(personalIdSessionData.getBackupCode()), new Date(), photoAsBase64,
                personalIdSessionData.getDemoUser(),personalIdSessionData.getRequiredLock(),
                personalIdSessionData.getInvitedUser());
        ConnectUserDatabaseUtil.storeUser(requireActivity(), user);
    }

    private void clearError() {
        viewBinding.errorTextView.setVisibility(View.GONE);
        viewBinding.errorTextView.setText("");
    }

    private void showError(String error) {
        viewBinding.errorTextView.setText(error);
        viewBinding.errorTextView.setVisibility(View.VISIBLE);
    }

    private void displayImage(String photoAsBase64) {
        viewBinding.photoImageView.setImageBitmap(MediaUtil.decodeBase64EncodedBitmap(photoAsBase64));
    }

    private void executeTakePhoto() {
        disableTakePhotoButton();
        Intent intent = new Intent(requireContext(), MicroImageActivity.class);
        intent.putExtra(MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, PHOTO_MAX_DIMENSION_PX);
        intent.putExtra(MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, PHOTO_MAX_SIZE_BYTES);
        takePhotoLauncher.launch(intent);
    }

    private void logAndShowAccountComplete() {
        FirebaseAnalyticsUtil.reportPersonalIdAccountCreated();
        navigateToMessageDisplay(
                        getString(R.string.connect_register_success_title),
                        getString(R.string.connect_register_success_message),
                        false,
                        ConnectConstants.PERSONALID_REGISTRATION_SUCCESS,
                        R.string.connect_register_success_button);
    }

    private void disableTakePhotoButton() {
        viewBinding.takePhotoButton.setEnabled(false);
    }

    private void enableTakePhotoButton() {
        viewBinding.takePhotoButton.setEnabled(true);
    }

    @Override
    protected void navigateToMessageDisplay(@NotNull String title,
            @org.jetbrains.annotations.Nullable String message, boolean isCancellable, int phase, int buttonText) {
        NavDirections directions = PersonalIdPhotoCaptureFragmentDirections
                .actionPersonalidPhotoCaptureToPersonalidMessage(title, message, phase, getString(buttonText), null)
                .setIsCancellable(isCancellable);
        Navigation.findNavController(viewBinding.getRoot()).navigate(directions);
    }
}

package org.commcare.fragments.connectId;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.databinding.ScreenConnectidPhotoCaptureBinding;
import org.commcare.fragments.MicroImageActivity;
import org.commcare.utils.MediaUtil;

import java.io.InputStream;

/**
 * Screen to capture user's photo as part of Connect ID Account management process
 */
public class ConnectIdPhotoCaptureFragment extends Fragment {

    private ActivityResultLauncher<Intent> takePhotoLauncher;
    private @NonNull ScreenConnectidPhotoCaptureBinding viewBinding;
    private ConnectUserRecord connectUserRecord;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewBinding = ScreenConnectidPhotoCaptureBinding.inflate(inflater, container, false);
        connectUserRecord = ConnectIDManager.getInstance().getUser(getContext());
        initTakePhotoLauncher();
        setUpUi();
        return viewBinding.getRoot();
    }

    private void initTakePhotoLauncher() {
        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String photoAsBase64 = result.getData().getStringExtra(MicroImageActivity.MICRO_IMAGE_BASE_64_RESULT_KEY);
                        displayImage(photoAsBase64);
                        uploadImage(photoAsBase64);
                    }
                }
        );
    }

    private void setUpUi() {
        viewBinding.takePhotoButton.setOnClickListener(v -> executeTakePhoto());
    }

    private void uploadImage(String photoAsBase64) {
        IApiCallback networkResponseCallback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                onPhotoUploadSuccess(photoAsBase64);
            }

            @Override
            public void processFailure(int responseCode) {
                onPhotoUploadFailure();
            }

            @Override
            public void processNetworkFailure() {
                onPhotoUploadFailure();
                ConnectNetworkHelper.showNetworkError(getContext());
            }

            @Override
            public void processTokenUnavailableError() {
                onPhotoUploadFailure();
                ConnectNetworkHelper.handleTokenUnavailableException(requireActivity());
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException(requireActivity());
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(getContext());
            }
        };
        ApiConnectId.updatePhoto(requireContext(), connectUserRecord.getUserId(), connectUserRecord.getPassword(),
                photoAsBase64, networkResponseCallback);
    }

    private void onPhotoUploadFailure() {
        showError();
        enableTakePhotoButton();
        enableSaveButton();
    }

    private void enableSaveButton() {
    }

    private void onPhotoUploadSuccess(String photoAsBase64) {
        clearError();
        enableTakePhotoButton();
        savePhotoToDatabase(photoAsBase64);
    }

    private void savePhotoToDatabase(String photoAsBase64) {

    }

    private void clearError() {

    }

    private void showError() {

    }

    private void displayImage(String photoAsBase64) {
        Bitmap bitmap = MediaUtil.decodeBase64EncodedBitmap(photoAsBase64);
        viewBinding.photoImageView.setImageBitmap(bitmap);
    }

    private void executeTakePhoto() {
        disableTakePhotoButton();
        Intent intent = new Intent(requireContext(), MicroImageActivity.class);
        takePhotoLauncher.launch(intent);
    }

    private void disableTakePhotoButton() {
        viewBinding.takePhotoButton.setEnabled(false);
    }

    private void enableTakePhotoButton() {
        viewBinding.takePhotoButton.setEnabled(true);
    }
}

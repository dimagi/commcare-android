package org.commcare.fragments.personalId;

import static org.commcare.fragments.MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA;
import static org.commcare.fragments.MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA;

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
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiPersonalId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ScreenPersonalidPhotoCaptureBinding;
import org.commcare.fragments.MicroImageActivity;
import org.commcare.utils.MediaUtil;

import java.io.InputStream;

/**
 * Screen to capture user's photo as part of Connect ID Account management process
 */
public class PersonalIdPhotoCaptureFragment extends Fragment {

    private static final int PHOTO_MAX_DIMENSION_PX = 160;
    private static final int PHOTO_MAX_SIZE_BYTES = 100 * 1024; // 100 KB
    private ActivityResultLauncher<Intent> takePhotoLauncher;
    private @NonNull ScreenPersonalidPhotoCaptureBinding viewBinding;
    private ConnectUserRecord connectUserRecord;
    private String photoAsBase64;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewBinding = ScreenPersonalidPhotoCaptureBinding.inflate(inflater, container, false);
        connectUserRecord = PersonalIdManager.getInstance().getUser(getContext());
        initTakePhotoLauncher();
        setUpUi();
        return viewBinding.getRoot();
    }

    private void initTakePhotoLauncher() {
        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        photoAsBase64 = result.getData().getStringExtra(MicroImageActivity.MICRO_IMAGE_BASE_64_RESULT_KEY);
                        displayImage(photoAsBase64);
                        enableSaveButton();
                    }
                    enableTakePhotoButton();
                }
        );
    }

    private void setUpUi() {
        viewBinding.title.setText(getString(R.string.connectid_photo_capture_title, getUserName()));
        viewBinding.takePhotoButton.setOnClickListener(v -> executeTakePhoto());
        viewBinding.savePhotoButton.setOnClickListener(v -> uploadImage());
    }

    private String getUserName() {
        if (getArguments() != null) {
            return PersonalIdPhotoCaptureFragmentArgs.fromBundle(getArguments()).getUserName();
        }
        return "";
    }

    private void uploadImage() {
        IApiCallback networkResponseCallback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                onPhotoUploadSuccess(photoAsBase64);
            }

            @Override
            public void processFailure(int responseCode) {
                onPhotoUploadFailure(requireContext().getString(R.string.connectid_photo_upload_failure), true);
            }

            @Override
            public void processNetworkFailure() {
                onPhotoUploadFailure(requireContext().getString(R.string.recovery_network_unavailable), true);
            }

            @Override
            public void processTokenUnavailableError() {
                onPhotoUploadFailure(requireContext().getString(R.string.recovery_network_token_unavailable), true);
            }

            @Override
            public void processTokenRequestDeniedError() {
                onPhotoUploadFailure(requireContext().getString(R.string.recovery_network_token_request_rejected),
                        false);
            }

            @Override
            public void processOldApiError() {
                onPhotoUploadFailure(requireContext().getString(R.string.recovery_network_outdated), false);
            }
        };
        ApiPersonalId.updatePhoto(requireContext(), connectUserRecord.getUserId(), connectUserRecord.getPassword(),
                connectUserRecord.getName(), photoAsBase64, networkResponseCallback);
    }

    private void onPhotoUploadFailure(String error, boolean allowRetry) {
        showError(error);
        if (allowRetry) {
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
        clearError();
        enableTakePhotoButton();
        disableSaveButton();
        savePhotoToDatabase(photoAsBase64);
        showAccountComplete();
    }

    private void savePhotoToDatabase(String photoAsBase64) {
        connectUserRecord.setPhoto(photoAsBase64);
        ConnectUserDatabaseUtil.storeUser(requireContext(), connectUserRecord);
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
        Bitmap bitmap = MediaUtil.decodeBase64EncodedBitmap(photoAsBase64);
        viewBinding.photoImageView.setImageBitmap(bitmap);
    }

    private void executeTakePhoto() {
        disableTakePhotoButton();
        Intent intent = new Intent(requireContext(), MicroImageActivity.class);
        intent.putExtra(MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, PHOTO_MAX_DIMENSION_PX);
        intent.putExtra(MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, PHOTO_MAX_SIZE_BYTES);
        takePhotoLauncher.launch(intent);
    }

    private void showAccountComplete() {
        NavDirections directions =
                PersonalIdPhotoCaptureFragmentDirections.actionPersonalidPhotoCaptureToPersonalidMessage(
                        getString(R.string.connect_register_success_title),
                        getString(R.string.connect_register_success_message),
                        ConnectConstants.PERSONALID_REGISTRATION_SUCCESS,
                        getString(R.string.connect_register_success_button),
                        null, "", null).setIsCancellable(false);
        Navigation.findNavController(viewBinding.savePhotoButton).navigate(directions);
    }

    private void disableTakePhotoButton() {
        viewBinding.takePhotoButton.setEnabled(false);
    }

    private void enableTakePhotoButton() {
        viewBinding.takePhotoButton.setEnabled(true);
    }
}

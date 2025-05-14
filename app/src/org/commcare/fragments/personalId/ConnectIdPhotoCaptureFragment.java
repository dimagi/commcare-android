package org.commcare.fragments.connectId;

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

import org.commcare.dalvik.databinding.ScreenConnectidPhotoCaptureBinding;
import org.commcare.fragments.MicroImageActivity;

/**
 * Screen to capture user's photo as part of Connect ID Account management process
 */
public class ConnectIdPhotoCaptureFragment extends Fragment {

    private ActivityResultLauncher<Intent> takePhotoLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        ScreenConnectidPhotoCaptureBinding binding = ScreenConnectidPhotoCaptureBinding.inflate(inflater, container, false);
        initTakePhotoLauncher();
        setUpUi(binding);
        return binding.getRoot();
    }

    private void initTakePhotoLauncher() {
        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        /**
                         * 1. extract photo
                         * 2. show it on screen
                         * 3. On save, make an API call to save it on server
                         */
                    }
                }
        );
    }

    private void setUpUi(ScreenConnectidPhotoCaptureBinding binding) {
        binding.takePhotoButton.setOnClickListener(v -> executeTakePhoto());
    }

    private void executeTakePhoto() {
        Intent intent = new Intent(requireContext(), MicroImageActivity.class);
        takePhotoLauncher.launch(intent);
    }
}

package org.commcare.fragments;

import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import org.commcare.dalvik.R;
import org.commcare.views.FaceCaptureView;

import java.util.concurrent.ExecutionException;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

public class MicroImageActivity extends AppCompatActivity {
    private static final String TAG = MicroImageActivity.class.toString();
    private PreviewView cameraView;
    private FaceCaptureView faceCaptureView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.micro_image_widget);

        faceCaptureView = findViewById(R.id.face_overlay);
        cameraView = findViewById(R.id.view_finder);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.micro_image_activity_title);
        }

        try {
           startCamera();
        } catch (ExecutionException | InterruptedException e) {
            logErrorAndExit("Error starting camera", e);
        }
    }

    private void startCamera() throws ExecutionException, InterruptedException {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            ProcessCameraProvider cameraProvider = null;
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                logErrorAndExit("Error acquiring camera provider", e);
            }
            bindUseCases(cameraProvider);
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases(ProcessCameraProvider cameraProvider) {
        int targetRotation = getWindowManager().getDefaultDisplay().getRotation();
        Size targetResolution = new Size(faceCaptureView.getImageWidth(), faceCaptureView.getImageHeight());

        // Preview use case
        Preview preview = new Preview.Builder()
                .setTargetResolution(targetResolution)
                .setTargetRotation(targetRotation)
                .build();
        preview.setSurfaceProvider(cameraView.getSurfaceProvider());

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        // Unbind any previous use cases before binding new ones
        cameraProvider.unbindAll();

        // Bind the use cases to the camera
        cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    private void logErrorAndExit(String logMessage, Throwable e) {
        Log.e(TAG, logMessage + ": " + e);
        Toast.makeText(this, R.string.camera_start_failed, Toast.LENGTH_LONG).show();
        setResult(AppCompatActivity.RESULT_CANCELED);
        finish();
    }
}

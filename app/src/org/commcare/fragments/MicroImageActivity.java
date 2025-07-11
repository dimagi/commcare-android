package org.commcare.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.os.Bundle;
import android.util.Base64;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.common.internal.ImageConvertUtils;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.commcare.dalvik.R;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.FileUtil;
import org.commcare.utils.ImageSizeTooLargeException;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.Permissions;
import org.commcare.views.FaceCaptureView;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCase;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

public class MicroImageActivity extends AppCompatActivity implements ImageAnalysis.Analyzer, FaceCaptureView.ImageStabilizedListener, RuntimePermissionRequester {
    public static final String MICRO_IMAGE_BASE_64_RESULT_KEY = "micro_image_base_64_result_key";
    public static final String MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA = "micro_image_max_dimension_px_extra";
    public static final String MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA = "micro_image_max_size_bytes_extra";
    private static final int DEFAULT_MICRO_IMAGE_MAX_DIMENSION_PX = 72;
    private static final int DEFAULT_MICRO_IMAGE_MAX_SIZE_BYTES = 2 * 1024;
    public static final String BASE_64_IMAGE_PREFIX = "data:image/webp;base64,";

    private PreviewView cameraView;
    private FaceCaptureView faceCaptureView;
    private Bitmap inputImage;
    private ImageView cameraShutterButton;
    private boolean isGooglePlayServicesAvailable = false;

    ActivityResultLauncher<String> cameraPermissionRequestLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    logErrorAndExit("Error acquiring camera permission", "microimage.camera.permission.denied", null);
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.micro_image_widget);

        faceCaptureView = findViewById(R.id.face_overlay);
        cameraView = findViewById(R.id.view_finder);
        cameraShutterButton = findViewById(R.id.camera_shutter_button);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.micro_image_activity_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        isGooglePlayServicesAvailable = AndroidUtil.isGooglePlayServicesAvailable(this);
        if (isGooglePlayServicesAvailable) {
            faceCaptureView.setImageStabilizedListener(this);
        } else {
            faceCaptureView.setCaptureMode(FaceCaptureView.CaptureMode.ManualMode);
            cameraShutterButton.setVisibility(View.VISIBLE);
        }
        checkForCameraPermission();
    }

    private void checkForCameraPermission() {
        if (Permissions.missingAppPermission(this, Manifest.permission.CAMERA)) {
            if (Permissions.shouldShowPermissionRationale(this, Manifest.permission.CAMERA)) {
                CommCareAlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                                -1, // actually not required due to launcher activity
                                getString(R.string.personalid_camera_permission_title),
                                getString(R.string.personalid_camera_permission_msg));
                dialog.showNonPersistentDialog(this);
            } else {
                requestNeededPermissions(-1);
            }
        } else {
            startCamera();
        }
    }

    @Override
    public void requestNeededPermissions(int requestCode) {
        cameraPermissionRequestLauncher.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            ProcessCameraProvider cameraProvider;
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                logErrorAndExit("Error acquiring camera provider", "microimage.camera.start.failed", e);
                return;
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

        UseCase imageAnalyzerOrCapture;
        if (faceCaptureView.getCaptureMode() == FaceCaptureView.CaptureMode.FaceDetectionMode) {
            imageAnalyzerOrCapture = buildImageAnalysisUseCase(targetResolution, targetRotation);
        } else {
            imageAnalyzerOrCapture = buildImageCaptureUseCase(targetResolution);
        }
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        // Unbind any previous use cases before binding new ones
        cameraProvider.unbindAll();

        // Bind the use cases to the camera
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzerOrCapture);
    }

    private UseCase buildImageAnalysisUseCase(Size targetResolution, int targetRotation) {
        ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(targetResolution)
                .setTargetRotation(targetRotation)
                .build();

        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(getApplicationContext()), this);
        return imageAnalyzer;
    }

    private void logErrorAndExit(String logMessage, String userMessageKey, Throwable e) {
        if (e == null) {
            Logger.log(LogTypes.TYPE_EXCEPTION, logMessage);
        } else {
            Logger.exception(logMessage, e);
        }
        Toast.makeText(this, Localization.get(userMessageKey), Toast.LENGTH_LONG).show();
        setResult(AppCompatActivity.RESULT_CANCELED);
        finish();
    }

    // Set up image capture use case, for when Google Services is not available
    private UseCase buildImageCaptureUseCase(Size targetResolution) {
        ImageCapture imageCapture = new ImageCapture.Builder().setTargetResolution(targetResolution).build();

        cameraShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageCapture.takePicture(ContextCompat.getMainExecutor(getApplicationContext()), new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        super.onCaptureSuccess(imageProxy);
                        @SuppressLint("UnsafeOptInUsageError")
                        Image capturedImage = imageProxy.getImage();
                        if (capturedImage != null) {
                            inputImage = ImageConvertUtils.getInstance().convertJpegToUpRightBitmap(capturedImage, imageProxy.getImageInfo().getRotationDegrees());
                            imageProxy.close();
                            finalizeImageCapture(calcPreviewCaptureArea());
                        } else {
                            logErrorAndExit("No image found, manual capture failed!", "microimage.camera.start.failed", null);
                        }
                    }
                });
            }
        });
        return imageCapture;
    }

    private Rect calcPreviewCaptureArea() {
        int actualImageHeight = (int)(faceCaptureView.getImageHeight() * ((float)faceCaptureView.getHeight()) / cameraView.getHeight());
        int actualImageWidth = (int)(faceCaptureView.getImageWidth() * ((float)faceCaptureView.getWidth()) / cameraView.getWidth());
        RectF rectF = faceCaptureView.calcCaptureArea(actualImageWidth, actualImageHeight);
        return new Rect((int)rectF.left, (int)rectF.top, (int)rectF.right, (int)rectF.bottom);
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError") Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            FaceDetectorOptions realTimeOpts = new FaceDetectorOptions.Builder()
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .build();
            FaceDetector faceDetector = FaceDetection.getClient(realTimeOpts);
            // process image with the face detector
            faceDetector.process(image)
                    .addOnSuccessListener(faces -> processFaceDetectionResult(faces, image))
                    .addOnFailureListener(this::handleErrorDuringDetection)
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    private void handleErrorDuringDetection(Exception e) {
        Logger.exception("Error during face detection ", e);
        Toast.makeText(this, "microimage.face.detection.mode.failed", Toast.LENGTH_LONG).show();
        switchToManualCaptureMode();
    }

    private void processFaceDetectionResult(List<Face> faces, InputImage image) {
        if (faces.size() > 0) {
            // Only one face is processed, this can be increased if needed
            Face newFace = faces.get(0);

            // this will draw a bounding circle around the first detected face
            faceCaptureView.updateFace(newFace);
            try {
                inputImage = ImageConvertUtils.getInstance().convertToUpRightBitmap(image);
            } catch (MlKitException e) {
                handleErrorDuringDetection(e);
            }
        } else {
            faceCaptureView.updateFace(null);
        }
    }

    private void switchToManualCaptureMode() {
        cameraShutterButton.setVisibility(View.VISIBLE);
        isGooglePlayServicesAvailable = false;
        faceCaptureView.setCaptureMode(FaceCaptureView.CaptureMode.ManualMode);
        startCamera();
    }

    @Override
    public void onImageStabilizedListener(Rect faceArea) {
        finalizeImageCapture(faceArea);
    }

    private void finalizeImageCapture(Rect faceArea) {
        Bitmap croppedBitmap = null;
        Bitmap scaledBitmap = null;
        try {
            int paddingXPx = 10;
            int paddingYPy = 50;
            Rect safeFaceArea = new Rect(
                    Math.max(0, faceArea.left - paddingXPx),
                    Math.max(0, faceArea.top - paddingYPy),
                    Math.min(inputImage.getWidth(), faceArea.right + paddingXPx),
                    Math.min(inputImage.getHeight(), faceArea.bottom + paddingYPy)
            );
            croppedBitmap = MediaUtil.cropImage(inputImage, safeFaceArea);
            scaledBitmap = FileUtil.getBitmapScaledByMaxDimen(croppedBitmap, getMaxDimensionSize());
            if (scaledBitmap == null) {
                scaledBitmap = croppedBitmap;
            }
            byte[] compressedByteArray = MediaUtil.compressBitmapToTargetSize(scaledBitmap, getMaxImageSize());
            String finalImageAsBase64 = BASE_64_IMAGE_PREFIX + Base64.encodeToString(compressedByteArray, Base64.DEFAULT);
            finishWithResul(finalImageAsBase64);
        } catch (IOException | ImageSizeTooLargeException e) {
            logErrorAndExit(e.getMessage(), "microimage.cropping.failed", e.getCause());
        } finally {
            recycleBitmap(croppedBitmap);
            recycleBitmap(scaledBitmap);
        }
    }

    private int getMaxImageSize() {
        return getIntent().getIntExtra(MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, DEFAULT_MICRO_IMAGE_MAX_SIZE_BYTES);
    }

    private int getMaxDimensionSize() {
        return getIntent().getIntExtra(MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, DEFAULT_MICRO_IMAGE_MAX_DIMENSION_PX);
    }

    private void finishWithResul(String finalImageAsBase64) {
        Intent result = new Intent();
        result.putExtra(MICRO_IMAGE_BASE_64_RESULT_KEY, finalImageAsBase64);
        setResult(AppCompatActivity.RESULT_OK, result);
        finish();
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}

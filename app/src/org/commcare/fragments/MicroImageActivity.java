package org.commcare.fragments;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.os.Bundle;
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
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.MediaUtil;
import org.commcare.views.FaceCaptureView;
import org.commcare.views.widgets.ImageWidget;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.util.List;
import java.util.concurrent.ExecutionException;

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

public class MicroImageActivity extends AppCompatActivity implements ImageAnalysis.Analyzer, FaceCaptureView.ImageStabilizedListener {
    private static final String TAG = MicroImageActivity.class.toString();
    private PreviewView cameraView;
    private FaceCaptureView faceCaptureView;
    private Bitmap inputImage;
    private ImageView cameraShutterButton;

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
        }

        if (!AndroidUtil.isGooglePlayServicesAvailable(this)) {
            faceCaptureView.setCaptureMode(FaceCaptureView.CaptureMode.ManualMode);
            cameraShutterButton.setVisibility(View.VISIBLE);
        } else {
            faceCaptureView.setImageStabilizedListener(this);
        }

        try {
           startCamera();
        } catch (ExecutionException | InterruptedException e) {
            logErrorAndExit("Error starting camera", "microimage.camera.start.failed", e);
        }
    }

    private void startCamera() throws ExecutionException, InterruptedException {
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

        UseCase imageAnalyzer = null;
        if (faceCaptureView.getCaptureMode() == FaceCaptureView.CaptureMode.FaceDetectionMode) {
            imageAnalyzer = buildImageAnalysisUseCase(targetResolution, targetRotation);
        } else {
            imageAnalyzer = buildImageCaptureUseCase(targetResolution);
        }
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        // Unbind any previous use cases before binding new ones
        cameraProvider.unbindAll();

        // Bind the use cases to the camera
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
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
                    .addOnFailureListener(e -> handleErrorDuringDetection(e))
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                    });
        } else {
            imageProxy.close();
        }
    }

    private void handleErrorDuringDetection(Exception e) {
        Logger.exception("Error during face detection ", e);
        Toast.makeText(this, "microimage.face.detection.mode.failed", Toast.LENGTH_LONG).show();
        // TODO: decide whether to switch to manual mode or close activity
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
                Logger.exception("Error during face detection ", e);
                Toast.makeText(this, "microimage.face.detection.mode.failed", Toast.LENGTH_LONG).show();
                // TODO: decide whether to switch to manual mode or close activity?
            }
        } else {
            faceCaptureView.updateFace(null);
        }
    }

    @Override
    public void onImageStabilizedListener(Rect faceArea) {
        finalizeImageCapture(faceArea);
    }

    private void finalizeImageCapture(Rect faceArea) {
        try {
            MediaUtil.cropAndSaveImage(inputImage, faceArea, ImageWidget.getTempFileForImageCapture());
            setResult(AppCompatActivity.RESULT_OK);
            finish();
        } catch (Exception e) {
            logErrorAndExit(e.getMessage(), "microimage.cropping.failed", e.getCause());
        }

    }

    // Set up image capture use case, for when Google Services is not available
    private UseCase buildImageCaptureUseCase(Size targetResolution){
        ImageCapture imageCapture = new ImageCapture.Builder().setTargetResolution(targetResolution).build();

        cameraShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageCapture.takePicture(ContextCompat.getMainExecutor(getApplicationContext()), new ImageCapture.OnImageCapturedCallback(){
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        super.onCaptureSuccess(imageProxy);
                        @SuppressLint("UnsafeOptInUsageError")
                        Image capturedImage = imageProxy.getImage();
                        inputImage = ImageConvertUtils.getInstance().convertJpegToUpRightBitmap(capturedImage, imageProxy.getImageInfo().getRotationDegrees());
                        imageProxy.close();
                        finalizeImageCapture(convertRectFToRect(faceCaptureView.getFaceCaptureArea()));
                    }
                });
            }
        });
        return imageCapture;
    }

    private Rect convertRectFToRect(RectF rectf) {
        return new Rect((int)rectf.left, (int)rectf.top, (int)rectf.right, (int)rectf.bottom);
    }
}

package org.commcare.activities.camera;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.util.Base64;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.common.internal.ImageConvertUtils;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.commcare.dalvik.R;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.FileUtil;
import org.commcare.utils.ImageSizeTooLargeException;
import org.commcare.utils.MediaUtil;
import org.commcare.views.FaceCaptureView;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.UseCase;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

public class MicroImageActivity extends BaseCameraActivity implements ImageAnalysis.Analyzer, FaceCaptureView.ImageStabilizedListener {
    public static final String MICRO_IMAGE_BASE_64_RESULT_KEY = "micro_image_base_64_result_key";
    public static final String MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA = "micro_image_max_dimension_px_extra";
    public static final String MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA = "micro_image_max_size_bytes_extra";
    private static final int DEFAULT_MICRO_IMAGE_MAX_DIMENSION_PX = 72;
    private static final int DEFAULT_MICRO_IMAGE_MAX_SIZE_BYTES = 2 * 1024;
    public static final String BASE_64_IMAGE_PREFIX = "data:image/webp;base64,";

    private FaceCaptureView faceCaptureView;
    private Bitmap inputImage;
    private ImageView cameraShutterButton;
    private boolean isGooglePlayServicesAvailable = false;

    @Override
    protected int getContentLayout() {
        return R.layout.micro_image_widget;
    }

    @Override
    protected int getTitleRes() {
        return R.string.micro_image_activity_title;
    }

    @NonNull
    @Override
    protected PreviewView getCameraView() {
        return findViewById(R.id.view_finder);
    }

    @Override
    protected CameraSelector getCameraSelector() {
        return CameraSelector.DEFAULT_FRONT_CAMERA;
    }

    @NonNull
    @Override
    protected Size getTargetResolution() {
        return new Size(faceCaptureView.getImageWidth(), faceCaptureView.getImageHeight());
    }

    @Override
    protected void onCameraViewReady() {
        faceCaptureView = findViewById(R.id.face_overlay);
        cameraShutterButton = findViewById(R.id.camera_shutter_button);
        isGooglePlayServicesAvailable = AndroidUtil.isGooglePlayServicesAvailable(this);
        if (isGooglePlayServicesAvailable) {
            faceCaptureView.setImageStabilizedListener(this);
        } else {
            faceCaptureView.setCaptureMode(FaceCaptureView.CaptureMode.ManualMode);
            cameraShutterButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected UseCase buildCaptureUseCase(Size targetResolution, int targetRotation) {
        if (faceCaptureView.getCaptureMode() == FaceCaptureView.CaptureMode.FaceDetectionMode) {
            return buildImageAnalysisUseCase(targetResolution, targetRotation);
        } else {
            return buildImageCaptureUseCase(targetResolution, targetRotation);
        }
    }

    private UseCase buildImageAnalysisUseCase(Size targetResolution, int targetRotation) {
        ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(buildResolutionSelector(targetResolution))
                .setTargetRotation(targetRotation)
                .build();

        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(getApplicationContext()), this);
        return imageAnalyzer;
    }

    // Set up image capture use case, for when Google Services is not available
    private UseCase buildImageCaptureUseCase(Size targetResolution, int targetRotation) {
        ImageCapture imageCapture = new ImageCapture.Builder()
                .setResolutionSelector(buildResolutionSelector(targetResolution))
                .setTargetRotation(targetRotation)
                .build();

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

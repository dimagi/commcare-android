package org.commcare.views.media;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Barrier;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Constraints;
import com.bumptech.glide.Glide;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.mediadownload.MissingMediaDownloadHelper;
import org.commcare.mediadownload.MissingMediaDownloadResult;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.FileUtil;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.QRCodeEncoder;
import org.commcare.utils.StringUtils;
import org.commcare.views.ResizingImageView;
import org.commcare.views.ViewUtil;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import java.io.File;

/**
 * @author $|-|!˅@M
 */
public class MediaLayout extends ConstraintLayout {

    private static final String TAG = MediaLayout.class.getSimpleName();
    private static final String IMAGE_GIF_EXTENSION = ".gif";

    private AudioPlaybackButton audioButton;
    private ImageButton videoButton;
    private FrameLayout textViewContainer;
    private CommCareVideoView videoView;
    private ImageView qrView;
    private ImageView imageView;
    private ResizingImageView resizingImageView;
    private ImageView downloadIcon;
    private ProgressBar progressBar;
    private TextView missingMediaText;
    private ImageView divider;
    private Barrier mediaLayoutBottomBarrier;
    private View missingMediaBackground;

    public MediaLayout(@NonNull Context context) {
        super(context);
        initView(context);
    }

    public MediaLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public MediaLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    public static MediaLayout buildAudioImageLayout(Context context, TextView text, String audioURI, String imageURI) {
        MediaLayout mediaLayout = new MediaLayout(context);
        mediaLayout.setAVT(text, audioURI, imageURI, null, null, null, null, true, 0);
        return mediaLayout;
    }

    public static MediaLayout buildAudioImageVisualLayout(Context context,
                                                          TextView text, String audioURI, String imageURI,
                                                          final String videoURI, final String bigImageURI) {
        MediaLayout mediaLayout = new MediaLayout(context);
        mediaLayout.setAVT(text, audioURI, imageURI, videoURI, bigImageURI, null, null, false, 0);
        return mediaLayout;
    }

    public static MediaLayout buildComprehensiveLayout(Context context,
                                                       TextView text, String audioURI, String imageURI,
                                                       final String videoURI, final String bigImageURI,
                                                       final String qrCodeContent, String inlineVideoURI,
                                                       int questionIndex) {
        MediaLayout mediaLayout = new MediaLayout(context);
        mediaLayout.setAVT(text, audioURI, imageURI, videoURI, bigImageURI, qrCodeContent, inlineVideoURI, false, questionIndex);
        return mediaLayout;
    }

    public void addDivider() {
        divider.setVisibility(VISIBLE);
    }

    //region private helpers

    private void setAVT(TextView text, String audioURI, String imageURI,
                        final String videoURI, final String bigImageURI,
                        final String qrCodeContent, String inlineVideoURI,
                        boolean showImageAboveText,
                        int questionIndex) {
        setupStandardAudio(audioURI, questionIndex);
        setupVideoButton(videoURI);

        // We only show one of the inline-video-view / qrview / image
        if (inlineVideoURI != null) {
            setupInlineVideoView(inlineVideoURI);
        } else if (qrCodeContent != null) {
            setupQRView(qrCodeContent);
        } else if (imageURI != null) {
            setupImage(imageURI, bigImageURI);
        }

        addTextView(text);
        if (showImageAboveText) {
            showMediaAboveText();
        }
    }

    private void showMediaAboveText() {
        // This step will change the layout constraints of the views in xml to shift
        // media(video, image qr ) above text.

        // First align the mediaView(including missing media view) to the top.
        alignMediaAtTop(videoView);
        alignMediaAtTop(qrView);
        alignMediaAtTop(imageView);
        alignMediaAtTop(resizingImageView);
        alignMediaAtTop(downloadIcon);
        alignMediaAtTop(progressBar);

        // Next align the text, audiobutton below mediaView.
        alignTextContainerBelowMediaView(audioButton);
        alignTextContainerBelowMediaView(textViewContainer);
    }

    private void alignMediaAtTop(View view) {
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        params.topToBottom = LayoutParams.UNSET;
        params.topToTop = this.getId();
        view.setLayoutParams(params);
    }

    private void alignTextContainerBelowMediaView(View view) {
        LayoutParams textContainerParams = (LayoutParams) view.getLayoutParams();
        textContainerParams.topToTop = LayoutParams.UNSET;
        textContainerParams.topToBottom = mediaLayoutBottomBarrier.getId();
        view.setLayoutParams(textContainerParams);
    }

    private void addTextView(TextView text) {
        textViewContainer.addView(text);
        textViewContainer.setVisibility(text.getVisibility());
    }

    private void setupStandardAudio(String audioURI, int questionIndex) {
        if (audioURI != null) {
            audioButton.modifyButtonForNewView(ViewId.buildListViewId(questionIndex), audioURI, true);
            audioButton.setVisibility(VISIBLE);
        }
    }

    private void setupVideoButton(String videoURI) {
        if (videoURI != null) {
            boolean mediaPresent = FileUtil.referenceFileExists(videoURI);
            videoButton.setImageResource(mediaPresent ? android.R.drawable.ic_media_play : R.drawable.update_download_icon);
            if (!mediaPresent) {
                AndroidUtil.showToast(getContext(), R.string.video_download_prompt);
            }
            videoButton.setOnClickListener(v -> {
                String videoFilename = "";
                try {
                    videoFilename = ReferenceManager.instance().DeriveReference(videoURI).getLocalURI();
                } catch (InvalidReferenceException e) {
                    Log.e(TAG, "Invalid reference exception");
                    e.printStackTrace();
                }

                File videoFile = new File(videoFilename);
                if (!videoFile.exists()) {
                    downloadMissingVideo(videoButton, videoURI);
                } else {
                    Intent i = new Intent("android.intent.action.VIEW");
                    Uri videoFileUri = FileUtil.getUriForExternalFile(getContext(), videoFile);
                    i.setDataAndType(videoFileUri, "video/*");
                    i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        getContext().startActivity(i);
                        FormEntryActivity.mFormController.getFormAnalyticsHelper().recordVideoPlaybackStart(videoFile);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(getContext(),
                                getContext().getString(R.string.activity_not_found, "view video"),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            videoButton.setVisibility(VISIBLE);
        }
    }

    private void downloadMissingVideo(ImageButton videoButton, String videoURI) {
        AndroidUtil.showToast(getContext(), R.string.media_download_started);
        MissingMediaDownloadHelper.requestMediaDownload(videoURI, result -> {
            if (result instanceof MissingMediaDownloadResult.Success) {
                boolean mediaPresent = FileUtil.referenceFileExists(videoURI);
                videoButton.setImageResource(mediaPresent ? android.R.drawable.ic_media_play : R.drawable.update_download_icon);
                AndroidUtil.showToast(getContext(), R.string.media_download_completed);
                videoButton.setVisibility(VISIBLE);
            } else if (result instanceof MissingMediaDownloadResult.InProgress) {
                AndroidUtil.showToast(getContext(), R.string.media_download_in_progress);
            } else {
                Toast.makeText(getContext(), result.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupQRView(String qrCodeContent) {
        Bitmap image;
        try {
            QRCodeEncoder qrCodeEncoder =
                    new QRCodeEncoder(qrCodeContent, getScreenMinimumDimension());
            image = qrCodeEncoder.encodeAsBitmap();
            qrView.setImageBitmap(image);
            qrView.setVisibility(VISIBLE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupImage(String imageURI, String bigImageURI) {
        try {
            final String imageFilename = ReferenceManager.instance().DeriveReference(imageURI).getLocalURI();
            final File imageFile = new File(imageFilename);
            if (imageFile.exists()) {
                DisplayMetrics metrics = this.getContext().getResources().getDisplayMetrics();
                int[] maxBounds = new int[] { metrics.widthPixels, metrics.heightPixels };
                Bitmap b = MediaUtil.inflateDisplayImage(getContext(), imageURI, maxBounds[0], maxBounds[1]);
                if (b != null) {
                    ImageView mImageView;
                    if (useResizingImageView()) {
                        resizingImageView.setImageURI(imageURI, bigImageURI);
                        mImageView = resizingImageView;
                    } else {
                        mImageView = imageView;
                    }
                    if (imageFilename.toLowerCase().endsWith(IMAGE_GIF_EXTENSION)) {
                        Glide.with(mImageView).asGif()
                                .override(b.getWidth(), b.getHeight())
                                .load(imageFilename)
                                .into(mImageView);
                        b.recycle();
                    } else {
                        mImageView.setImageBitmap(b);
                    }
                    mImageView.setVisibility(VISIBLE);
                }
            } else {
                // An error hasn't been logged. We should have an image, but the file doesn't
                // exist.
                showMissingMediaView(imageURI,
                        StringUtils.getStringRobust(getContext(), R.string.image_download_prompt),
                        true,
                        () -> {
                            hideMissingMediaView();
                            setupImage(imageURI, bigImageURI);
                        });
            }
        } catch (InvalidReferenceException e) {
            Log.e(TAG, "image invalid reference exception");
            e.printStackTrace();
            showMissingMediaView(imageURI, "Invalid reference: " + e.getReferenceString(), false, null);
        }
    }

    private void setupInlineVideoView(String inlineVideoURI) {
        try {
            final String videoFilename = ReferenceManager.instance().DeriveReference(inlineVideoURI).getLocalURI();
            final File videoFile = new File(videoFilename);
            if (!videoFile.exists()) {
                showMissingMediaView(inlineVideoURI,
                        StringUtils.getStringRobust(getContext(), R.string.video_download_prompt),
                        true,
                        () -> {
                            hideMissingMediaView();
                            setupInlineVideoView(inlineVideoURI);
                        });
            } else {
                final CommCareMediaController ctrl = new CommCareMediaController(this.getContext());
                ctrl.setId(AndroidUtil.generateViewId());
                videoView.setOnPreparedListener(mediaPlayer -> {
                    //Since MediaController will create a default set of controls and put them in a window floating above your application(From AndroidDocs)
                    //It would never follow the parent view's animation or scroll.
                    //So, adding the MediaController to the view hierarchy here.
                    FrameLayout frameLayout = (FrameLayout)ctrl.getParent();
                    ((ViewGroup)frameLayout.getParent()).removeView(frameLayout);

                    ConstraintLayout.LayoutParams params = new Constraints.LayoutParams(LayoutParams.MATCH_CONSTRAINT, LayoutParams.WRAP_CONTENT);
                    params.bottomToBottom = videoView.getId();
                    params.leftToLeft = videoView.getId();
                    params.rightToRight = videoView.getId();
                    int margin = this.getResources().getDimensionPixelSize(R.dimen.question_widget_side_padding);
                    params.leftMargin = margin;
                    params.rightMargin = margin;

                    ((ConstraintLayout)videoView.getParent()).addView(frameLayout, params);

                    ctrl.setAnchorView(videoView);
                    videoView.setMediaController(ctrl);
                    ctrl.show();
                });

                makeVideoViewVisible();
                videoView.setVideoPath(videoFilename);
                videoView.setListener(duration -> {
                    // Do not log events if the video is never played.
                    if (duration == 0) {
                        return;
                    }
                    FirebaseAnalyticsUtil.reportInlineVideoPlayEvent(videoFilename, FileUtil.getDuration(videoFile), duration);
                });
                videoView.setOnClickListener(v -> ViewUtil.hideVirtualKeyboard((Activity)getContext()));
                videoView.setVisibility(VISIBLE);
            }
        } catch (InvalidReferenceException ire) {
            Log.e(TAG, "invalid video reference exception");
            ire.printStackTrace();
            showMissingMediaView(inlineVideoURI, "Invalid reference: " + ire.getReferenceString(), false, null);
        }
    }

    /**
     * Without this code VideoView doesn't appear on the screen.
     * It's height is always 0(from LayoutInspector) even if you set it to match_parent.
     */
    private void makeVideoViewVisible() {
        //These surprisingly get re-jiggered as soon as the video is loaded, so we
        //just want to give it the _max_ bounds, it'll pick the limiter and shrink
        //itself when it's ready.
        DisplayMetrics metrics = this.getContext().getResources().getDisplayMetrics();
        ViewGroup.LayoutParams params = videoView.getLayoutParams();
        params.width = metrics.widthPixels;
        params.height = metrics.heightPixels;
        videoView.setLayoutParams(params);
    }

    private void showMissingMediaView(String mediaUri, String errorMessage, boolean allowDownload, @Nullable Runnable completion) {
        missingMediaBackground.setVisibility(VISIBLE);
        missingMediaText.setText(errorMessage);
        missingMediaText.setVisibility(VISIBLE);
        downloadIcon.setVisibility(allowDownload ? View.VISIBLE : INVISIBLE);

        downloadIcon.setOnClickListener(v -> {

            progressBar.setVisibility(VISIBLE);
            downloadIcon.setVisibility(INVISIBLE);
            downloadIcon.setEnabled(false);
            missingMediaText.setText(StringUtils.getStringRobust(getContext(), R.string.media_download_in_progress));

            MissingMediaDownloadHelper.requestMediaDownload(mediaUri, result -> {
                if (result instanceof MissingMediaDownloadResult.Success) {
                    AndroidUtil.showToast(getContext(), R.string.media_download_completed);
                    if (completion != null) {
                        completion.run();
                    }
                } else if (!(result instanceof MissingMediaDownloadResult.InProgress)) {
                    progressBar.setVisibility(GONE);
                    downloadIcon.setVisibility(VISIBLE);
                    downloadIcon.setEnabled(true);
                    missingMediaText.setText(StringUtils.getStringRobust(getContext(), R.string.media_download_failed));
                }
            });
        });
    }

    private void hideMissingMediaView() {
        progressBar.setVisibility(GONE);
        downloadIcon.setVisibility(GONE);
        missingMediaText.setVisibility(GONE);
        missingMediaBackground.setVisibility(GONE);
    }

    @SuppressWarnings("deprecation")
    private int getScreenMinimumDimension() {
        Display display =
                ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE))
                        .getDefaultDisplay();
        Point screenDims = new Point();
        display.getSize(screenDims);
        return Math.min(screenDims.x, screenDims.y);
    }

    private boolean useResizingImageView() {
        // only allow ResizingImageView to be used if not also using smart inflation
        return !HiddenPreferences.isSmartInflationEnabled() &&
                ("full".equals(ResizingImageView.resizeMethod)
                        || "half".equals(ResizingImageView.resizeMethod)
                        || "width".equals(ResizingImageView.resizeMethod));
    }

    private void initView(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.media_layout, this);
        audioButton = view.findViewById(R.id.audio_button);
        videoButton = view.findViewById(R.id.video_button);
        textViewContainer = view.findViewById(R.id.question_text_container);
        videoView = view.findViewById(R.id.inline_video_view);
        qrView = view.findViewById(R.id.qr_view);
        imageView = view.findViewById(R.id.image);
        resizingImageView = view.findViewById(R.id.resizing_image);
        downloadIcon = view.findViewById(R.id.download_media_icon);
        progressBar = view.findViewById(R.id.progress_bar);
        missingMediaText = view.findViewById(R.id.missing_media_tv);
        divider = view.findViewById(R.id.divider);
        mediaLayoutBottomBarrier = view.findViewById(R.id.media_barrier);
        missingMediaBackground = view.findViewById(R.id.missing_media_background);

        resetView();
    }

    private void resetView() {
        audioButton.setVisibility(GONE);
        videoButton.setVisibility(GONE);
        textViewContainer.setVisibility(GONE);
        videoView.setVisibility(GONE);
        qrView.setVisibility(GONE);
        imageView.setVisibility(GONE);
        resizingImageView.setVisibility(GONE);
        downloadIcon.setVisibility(GONE);
        progressBar.setVisibility(GONE);
        missingMediaText.setVisibility(GONE);
        missingMediaBackground.setVisibility(GONE);
        divider.setVisibility(GONE);
    }
    //endregion

}

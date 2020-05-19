package org.commcare.views.media;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
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
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.mediadownload.MissingMediaDownloadHelper;
import org.commcare.mediadownload.MissingMediaDownloadResult;
import org.commcare.preferences.DeveloperPreferences;
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

import androidx.annotation.IdRes;

/**
 * This layout is used anywhere we can have image/audio/video/text.
 * TODO: Put this in a layout file!!!!
 *
 * @author carlhartung
 */
public class MediaLayout extends RelativeLayout {
    private static final String TAG = MediaLayout.class.getSimpleName();

    @IdRes
    public static final int INLINE_VIDEO_PANE_ID = 99999;

    @IdRes
    private static final int QUESTION_TEXT_PANE_ID = 2342134;

    @IdRes
    private static final int AUDIO_BUTTON_ID = 3245345;

    @IdRes
    private static final int VIDEO_BUTTON_ID = 234982340;

    @IdRes
    private static final int IMAGE_VIEW_ID = 23423534;

    private static final String IMAGE_GIF_EXTENSION = ".gif";

    private TextView viewText;
    private AudioPlaybackButton audioButton;
    private ImageButton videoButton;
    private View missingMediaView;
    private String mInlineVideoUri;
    private String mImageURI;
    private String mBigImageURI;
    private String mQrCodeContent;
    private RelativeLayout mediaPane;

    private MediaLayout(Context c) {
        super(c);

        viewText = null;
        audioButton = null;
        missingMediaView = null;
        videoButton = null;
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

    private void setAVT(TextView text, String audioURI, String imageURI,
                        final String videoURI, final String bigImageURI,
                        final String qrCodeContent, String inlineVideoURI,
                        boolean showImageAboveText,
                        int questionIndex) {
        viewText = text;
        mInlineVideoUri = inlineVideoURI;
        mImageURI = imageURI;
        mBigImageURI = bigImageURI;
        mQrCodeContent = qrCodeContent;

        RelativeLayout questionTextPane = new RelativeLayout(this.getContext());
        questionTextPane.setId(QUESTION_TEXT_PANE_ID);

        setupStandardAudio(audioURI, questionIndex);
        setupVideoButton(videoURI);

        // Now set up the center view -- it is either an image, a QR Code, an inline video, or
        // expanded audio
        mediaPane = new RelativeLayout(getContext());

        LayoutParams mediaPaneParams = refreshMediaView();

        addAudioVideoButtonsToView(questionTextPane);

        showImageAboveText = showImageAboveText || DeveloperPreferences.imageAboveTextEnabled();
        addElementsToView(mediaPane, mediaPaneParams, questionTextPane, showImageAboveText);
    }

    private LayoutParams refreshMediaView() {
        RelativeLayout.LayoutParams mediaPaneParams =
                new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

        View mediaView = null;
        if (mInlineVideoUri != null) {
            mediaView = getInlineVideoView(mInlineVideoUri, mediaPaneParams);
        } else if (mQrCodeContent != null) {
            mediaView = setupQRView(mQrCodeContent);
        } else if (mImageURI != null) {
            mediaView = setupImage(mImageURI, mBigImageURI);
        }

        if (mediaView != null) {
            RelativeLayout.LayoutParams mediaViewParams =
                    new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mediaViewParams.addRule(CENTER_IN_PARENT, mediaView.getId());
            mediaPane.addView(mediaView, mediaViewParams);
        }

        return mediaPaneParams;
    }

    private void setupVideoButton(final String videoURI) {
        if (videoURI != null) {
            videoButton = new ImageButton(getContext());

            boolean mediaPresent = FileUtil.referenceFileExists(videoURI);
            videoButton.setImageResource(mediaPresent ? android.R.drawable.ic_media_play : R.drawable.update_download_icon);
            if(!mediaPresent) {
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
            videoButton.setId(VIDEO_BUTTON_ID);
        }
    }

    private void downloadMissingVideo(ImageButton videoButton, String videoURI) {
        AndroidUtil.showToast(getContext(), R.string.media_download_started);
        MissingMediaDownloadHelper.requestMediaDownload(videoURI, result -> {
            if (result instanceof MissingMediaDownloadResult.Success) {
                boolean mediaPresent = FileUtil.referenceFileExists(videoURI);
                videoButton.setImageResource(mediaPresent ? android.R.drawable.ic_media_play : R.drawable.update_download_icon);
                AndroidUtil.showToast(getContext(), R.string.media_download_completed);
            } else if (result instanceof MissingMediaDownloadResult.InProgress) {
                AndroidUtil.showToast(getContext(), R.string.media_download_in_progress);
            } else {
                Toast.makeText(getContext(), result.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addAudioVideoButtonsToView(RelativeLayout questionTextPane) {
        LayoutParams textParams =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        LayoutParams audioParams =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        LayoutParams videoParams =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        // Add the audioButton and videoButton (if applicable) and view
        // (containing text) to the relative layout.
        if (audioButton != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                audioParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                textParams.addRule(RelativeLayout.START_OF, audioButton.getId());
            } else {
                audioParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                textParams.addRule(RelativeLayout.LEFT_OF, audioButton.getId());
            }
            questionTextPane.addView(audioButton, audioParams);

            if (videoButton != null) {
                videoParams.addRule(RelativeLayout.BELOW, audioButton.getId());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    videoParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                } else {
                    videoParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                }
                questionTextPane.addView(videoButton, videoParams);
            }
        } else if (videoButton != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                videoParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                textParams.addRule(RelativeLayout.START_OF, videoButton.getId());
            } else {
                videoParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                textParams.addRule(RelativeLayout.LEFT_OF, videoButton.getId());
            }
            questionTextPane.addView(videoButton, videoParams);
        } else {
            //Audio and Video are both null, let text bleed to right
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                textParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            } else {
                textParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            }
        }
        if (viewText.getVisibility() != GONE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                textParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            } else {
                textParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            }
            questionTextPane.addView(viewText, textParams);
        }
    }

    private void setupStandardAudio(String audioURI, int questionIndex) {
        if (audioURI != null) {
            boolean mediaPresent = FileUtil.referenceFileExists(audioURI);
            audioButton = new AudioPlaybackButton(getContext(), audioURI,
                    ViewId.buildListViewId(questionIndex), true);
            // random ID to be used by the relative layout.
            audioButton.setId(AUDIO_BUTTON_ID);
        }
    }

    private View setupQRView(String qrCodeContent) {
        Bitmap image;
        int minimumDim = getScreenMinimumDimension();

        try {
            QRCodeEncoder qrCodeEncoder =
                    new QRCodeEncoder(qrCodeContent, minimumDim);

            image = qrCodeEncoder.encodeAsBitmap();

            ImageView imageView = new ImageView(getContext());
            imageView.setPadding(10, 10, 10, 10);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageBitmap(image);
            imageView.setId(IMAGE_VIEW_ID);
            return imageView;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private View setupImage(String imageURI, String bigImageURI) {
        View mediaPane = null;
        try {
            int[] maxBounds = getMaxCenterViewBounds();
            final String imageFilename = ReferenceManager.instance().DeriveReference(imageURI).getLocalURI();
            final File imageFile = new File(imageFilename);
            if (imageFile.exists()) {
                Bitmap b = MediaUtil.inflateDisplayImage(getContext(), imageURI, maxBounds[0],
                        maxBounds[1]);
                if (b != null) {
                    ImageView mImageView = new ImageView(getContext());
                    if (useResizingImageView()) {
                        mImageView = new ResizingImageView(getContext(), imageURI, bigImageURI);
                        mImageView.setAdjustViewBounds(true);
                        mImageView.setMaxWidth(maxBounds[0]);
                        mImageView.setMaxHeight(maxBounds[1]);
                    } else {
                        mImageView.setScaleType(ImageView.ScaleType.CENTER);
                    }
                    mImageView.setPadding(10, 10, 10, 10);
                    if (imageFilename.toLowerCase().endsWith(IMAGE_GIF_EXTENSION)) {
                        Glide.with(mImageView).asGif()
                                .override(b.getWidth(), b.getHeight())
                                .load(imageFilename)
                                .into(mImageView);
                        b.recycle();
                    } else {
                        mImageView.setImageBitmap(b);
                    }
                    mImageView.setId(IMAGE_VIEW_ID);
                    mediaPane = mImageView;
                }
            } else {
                // An error hasn't been logged. We should have an image, but the file doesn't
                // exist.
                mediaPane = getMissingMediaView(imageURI,
                        StringUtils.getStringRobust(getContext(), R.string.video_download_prompt),
                        true);
            }
        } catch (InvalidReferenceException e) {
            Log.e(TAG, "image invalid reference exception");
            e.printStackTrace();
        }
        return mediaPane;
    }

    private void addElementsToView(View mediaPane,
                                   RelativeLayout.LayoutParams mediaPaneParams,
                                   RelativeLayout questionTextPane, boolean showImageAboveText) {
        RelativeLayout.LayoutParams questionTextPaneParams =
                new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (mediaPane != null) {
            if (viewText.getVisibility() == GONE) {
                this.addView(questionTextPane, questionTextPaneParams);
                if (audioButton != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        mediaPaneParams.addRule(RelativeLayout.START_OF, audioButton.getId());
                    } else {
                        mediaPaneParams.addRule(RelativeLayout.LEFT_OF, audioButton.getId());
                    }
                    questionTextPane.addView(mediaPane, mediaPaneParams);
                }
                if (videoButton != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        mediaPaneParams.addRule(RelativeLayout.START_OF, videoButton.getId());
                    } else {
                        mediaPaneParams.addRule(RelativeLayout.LEFT_OF, videoButton.getId());
                    }
                    questionTextPane.addView(mediaPane, mediaPaneParams);
                }
            } else {
                if (showImageAboveText) {
                    mediaPaneParams.addRule(CENTER_HORIZONTAL);
                    this.addView(mediaPane, mediaPaneParams);
                    questionTextPaneParams.addRule(RelativeLayout.BELOW, mediaPane.getId());
                    this.addView(questionTextPane, questionTextPaneParams);
                } else {
                    this.addView(questionTextPane, questionTextPaneParams);
                    mediaPaneParams.addRule(RelativeLayout.BELOW, questionTextPane.getId());
                    mediaPaneParams.addRule(CENTER_HORIZONTAL);
                    this.addView(mediaPane, mediaPaneParams);
                }
            }
        } else {
            this.addView(questionTextPane, questionTextPaneParams);
        }
    }

    @SuppressWarnings("deprecation")
    private int getScreenMinimumDimension() {
        Display display =
                ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE))
                        .getDefaultDisplay();

        int width, height;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2) {
            width = display.getWidth();
            height = display.getHeight();
        } else {
            Point screenDims = new Point();
            display.getSize(screenDims);
            width = screenDims.x;
            height = screenDims.y;
        }

        return Math.min(width, height);
    }

    /**
     * Creates a video view for the provided URI or an error view elaborating why the video
     * couldn't be displayed.
     *
     * @param inlineVideoURI   JavaRosa Reference URI
     * @param viewLayoutParams the layout params that will be applied to the view. Expect to be
     *                         mutated by this method
     */
    private View getInlineVideoView(String inlineVideoURI, RelativeLayout.LayoutParams viewLayoutParams) {
        try {
            final String videoFilename = ReferenceManager.instance().DeriveReference(inlineVideoURI).getLocalURI();

            int[] maxBounds = getMaxCenterViewBounds();

            final File videoFile = new File(videoFilename);
            if (!videoFile.exists()) {
                return getMissingMediaView(inlineVideoURI,
                        StringUtils.getStringRobust(getContext(), R.string.video_download_prompt),
                        true);
            } else {
                //NOTE: This has odd behavior when you have a text input on the screen
                //since clicking the video view to bring up controls has weird effects.
                //since we shotgun grab the focus for the input widget.

                final CommCareMediaController ctrl = new CommCareMediaController(this.getContext());
                CommCareVideoView videoView = new CommCareVideoView(this.getContext());
                videoView.setOnPreparedListener(mediaPlayer -> {
                    //Since MediaController will create a default set of controls and put them in a window floating above your application(From AndroidDocs)
                    //It would never follow the parent view's animation or scroll.
                    //So, adding the MediaController to the view hierarchy here.
                    FrameLayout frameLayout = (FrameLayout)ctrl.getParent();
                    ((ViewGroup)frameLayout.getParent()).removeView(frameLayout);
                    LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                    params.addRule(ALIGN_BOTTOM, videoView.getId());
                    params.addRule(ALIGN_LEFT, videoView.getId());
                    params.addRule(ALIGN_RIGHT, videoView.getId());

                    ((RelativeLayout)videoView.getParent()).addView(frameLayout, params);

                    ctrl.setAnchorView(videoView);
                    videoView.setMediaController(ctrl);
                    ctrl.show();
                });

                videoView.setVideoPath(videoFilename);
                videoView.setListener(duration -> {
                    // Do not log events if the video is never played.
                    if (duration == 0) {
                        return;
                    }
                    FirebaseAnalyticsUtil.reportInlineVideoPlayEvent(videoFilename, FileUtil.getDuration(videoFile), duration);
                });

                videoView.setOnClickListener(v -> ViewUtil.hideVirtualKeyboard((Activity)getContext()));

                //These surprisingly get re-jiggered as soon as the video is loaded, so we
                //just want to give it the _max_ bounds, it'll pick the limiter and shrink
                //itself when it's ready.
                viewLayoutParams.width = maxBounds[0];
                viewLayoutParams.height = maxBounds[1];

                videoView.setId(INLINE_VIDEO_PANE_ID);
                return videoView;
            }
        } catch (InvalidReferenceException ire) {
            Log.e(TAG, "invalid video reference exception");
            ire.printStackTrace();
            return getMissingMediaView(inlineVideoURI, "Invalid reference: " + ire.getReferenceString(), false);
        }
    }

    private View getMissingMediaView(String mediaUri, String errorMessage, boolean download) {
        missingMediaView = LayoutInflater.from(getContext()).inflate(R.layout.missing_media_view, this, false);

        TextView status = missingMediaView.findViewById(R.id.missing_media_tv);
        status.setText(errorMessage);

        View progressView = missingMediaView.findViewById(R.id.progress_bar);
        View downloadIcon = missingMediaView.findViewById(R.id.download_media_icon);
        downloadIcon.setVisibility(download ? View.VISIBLE : INVISIBLE);

        downloadIcon.setOnClickListener(v -> {

            progressView.setVisibility(VISIBLE);
            downloadIcon.setVisibility(INVISIBLE);
            downloadIcon.setEnabled(false);
            status.setText(StringUtils.getStringRobust(getContext(), R.string.media_download_in_progress));

            MissingMediaDownloadHelper.requestMediaDownload(mediaUri, result -> {
                progressView.setVisibility(GONE);
                if (result instanceof MissingMediaDownloadResult.Success) {
                    AndroidUtil.showToast(getContext(), R.string.media_download_completed);
                    reAddMediaPane();
                } else if (!(result instanceof MissingMediaDownloadResult.InProgress)) {
                    downloadIcon.setVisibility(VISIBLE);
                    downloadIcon.setEnabled(true);
                    status.setText(StringUtils.getStringRobust(getContext(), R.string.media_download_failed));
                }
            });
        });
        return missingMediaView;
    }

    // for some reason unless we re-add the mediaPane the layout doesn't refreshes itself
    private void reAddMediaPane() {
        int index = indexOfChild(mediaPane);
        removeView(mediaPane);
        mediaPane.removeAllViews();
        LayoutParams mediaPaneParams = refreshMediaView();
        addView(mediaPane, index, mediaPaneParams);
    }

    private boolean useResizingImageView() {
        // only allow ResizingImageView to be used if not also using smart inflation
        return !HiddenPreferences.isSmartInflationEnabled() &&
                ("full".equals(ResizingImageView.resizeMethod)
                        || "half".equals(ResizingImageView.resizeMethod)
                        || "width".equals(ResizingImageView.resizeMethod));
    }

    /**
     * @return The appropriate max size of an image view pane in this widget. returned as an int
     * array of [width, height]
     */
    private int[] getMaxCenterViewBounds() {
        DisplayMetrics metrics = this.getContext().getResources().getDisplayMetrics();
        int maxWidth = metrics.widthPixels;
        int maxHeight = metrics.heightPixels;

        // subtract height for textview and buttons, if present
        if (viewText != null) {
            maxHeight = maxHeight - viewText.getHeight();
        }
        if (videoButton != null) {
            maxHeight = maxHeight - videoButton.getHeight();
        } else if (audioButton != null) {
            maxHeight = maxHeight - audioButton.getHeight();
        }

        // reduce by third for safety
        return new int[]{maxWidth, (2 * maxHeight) / 3};
    }

    /**
     * This adds a divider at the bottom of this layout. Used to separate
     * fields in lists.
     */
    public void addDivider(ImageView v) {
        RelativeLayout.LayoutParams dividerParams =
                new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (missingMediaView != null) {
            dividerParams.addRule(RelativeLayout.BELOW, missingMediaView.getId());
        } else if (videoButton != null) {
            dividerParams.addRule(RelativeLayout.BELOW, videoButton.getId());
        } else if (audioButton != null) {
            dividerParams.addRule(RelativeLayout.BELOW, audioButton.getId());
        } else if (viewText != null) {
            // No picture
            dividerParams.addRule(RelativeLayout.BELOW, viewText.getId());
        } else {
            Log.e(TAG, "Tried to add divider to uninitialized ATVWidget");
            return;
        }
        addView(v, dividerParams);
    }
}

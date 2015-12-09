package org.odk.collect.android.views.media;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.odk.collect.android.utilities.QRCodeEncoder;
import org.odk.collect.android.views.ResizingImageView;

import java.io.File;

/**
 * This layout is used anywhere we can have image/audio/video/text.
 * TODO: Put this in a layout file!!!!
 *
 * @author carlhartung
 */
public class MediaLayout extends RelativeLayout {
    private static final String t = "AVTLayout";

    private TextView mView_Text;
    private AudioButton mAudioButton;
    private ImageButton mVideoButton;
    private ResizingImageView mImageView;
    private TextView mMissingImage;

    public MediaLayout(Context c) {
        super(c);

        mView_Text = null;
        mAudioButton = null;
        mImageView = null;
        mMissingImage = null;
        mVideoButton = null;
    }

    public void setAVT(TextView text, String audioURI, String imageURI,
                       final String videoURI, final String bigImageURI) {
        setAVT(text, audioURI, imageURI, videoURI, bigImageURI, null);
    }

    public void setAVT(TextView text, String audioURI, String imageURI,
                       final String videoURI, final String bigImageURI, final String qrCodeContent) {
        setAVT(text, audioURI, imageURI, videoURI, bigImageURI, null, null);
    }

    public void setAVT(TextView text, String audioURI, String imageURI,
                       final String videoURI, final String bigImageURI,
                       final String qrCodeContent, String inlineVideoURI) {
        mView_Text = text;

        RelativeLayout.LayoutParams textParams =
            new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        RelativeLayout.LayoutParams audioParams =
            new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        RelativeLayout.LayoutParams videoParams =
            new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        RelativeLayout.LayoutParams questionTextPaneParams =
            new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        RelativeLayout.LayoutParams mediaPaneParams =
                new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

        RelativeLayout questionTextPane = new RelativeLayout(this.getContext());
        questionTextPane.setId(2342134);

        if (audioURI != null) {
            mAudioButton = new AudioButton(getContext(), audioURI, true);
             // random ID to be used by the relative layout.
            mAudioButton.setId(3245345);
        }

        // Then set up the video button
        if (videoURI != null) {
            mVideoButton = new ImageButton(getContext());
            mVideoButton.setImageResource(android.R.drawable.ic_media_play);
            mVideoButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String videoFilename = "";
                    try {
                        videoFilename =
                            ReferenceManager._().DeriveReference(videoURI).getLocalURI();
                    } catch (InvalidReferenceException e) {
                        Log.e(t, "Invalid reference exception");
                        e.printStackTrace();
                    }

                    File videoFile = new File(videoFilename);
                    if (!videoFile.exists()) {
                        // We should have a video clip, but the file doesn't exist.
                        String errorMsg =
                            getContext().getString(R.string.file_missing, videoFilename);
                        Log.e(t, errorMsg);
                        Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                        return;
                    }

                    Intent i = new Intent("android.intent.action.VIEW");
                    i.setDataAndType(Uri.fromFile(videoFile), "video/*");
                    try {
                        String uri = Uri.fromFile(videoFile).getPath().replaceAll("^.*\\/", "");
                        Logger.log("media", "start " + uri);
                        getContext().startActivity(i);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(getContext(),
                            getContext().getString(R.string.activity_not_found, "view video"),
                            Toast.LENGTH_SHORT).show();
                    }
                }
            });
            mVideoButton.setId(234982340);
        }

        // Add the audioButton and videoButton (if applicable) and view
        // (containing text) to the relative layout.
        if (mAudioButton != null && mVideoButton == null) {
            audioParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            textParams.addRule(RelativeLayout.LEFT_OF, mAudioButton.getId());
            questionTextPane.addView(mAudioButton, audioParams);
        } else if (mAudioButton == null && mVideoButton != null) {
            videoParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            textParams.addRule(RelativeLayout.LEFT_OF, mVideoButton.getId());
            questionTextPane.addView(mVideoButton, videoParams);
        } else if (mAudioButton != null && mVideoButton != null) {
            audioParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            textParams.addRule(RelativeLayout.LEFT_OF, mAudioButton.getId());
            videoParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            videoParams.addRule(RelativeLayout.BELOW, mAudioButton.getId());
            questionTextPane.addView(mAudioButton, audioParams);
            questionTextPane.addView(mVideoButton, videoParams);
        } else {
            //Audio and Video are both null, let text bleed to right
            textParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        }
        boolean textVisible = (mView_Text.getVisibility() != GONE);
        if (textVisible) {
            textParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            questionTextPane.addView(mView_Text, textParams);
        }

        // Now set up the center view, it is either an image, a QR Code, or an inline video
        String errorMsg = null;
        View mediaPane = null;

        if (inlineVideoURI != null) {
            mediaPane = getInlineVideoView(inlineVideoURI, mediaPaneParams);

        }
        else if (qrCodeContent != null ) {
            Bitmap image;
            Display display =
                    ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                            .getDefaultDisplay();

            //see if we're doing a new QR code display
            int screenWidth = display.getWidth();
            int screenHeight = display.getHeight();

            int minimumDim = Math.min(screenWidth,  screenHeight);

            try {
                QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(qrCodeContent,minimumDim);

                image = qrCodeEncoder.encodeAsBitmap();

                ImageView mImageView = new ImageView(getContext());
                mImageView.setPadding(10, 10, 10, 10);
                mImageView.setAdjustViewBounds(true);
                mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                mImageView.setImageBitmap(image);
                mImageView.setId(23423534);

                mediaPane = mImageView;
            } catch(Exception e) {
                e.printStackTrace();
            }
        } else if (imageURI != null) {
            try {
                int[] maxBounds = getMaxCenterViewBounds();
                final String imageFilename = ReferenceManager._().DeriveReference(imageURI).getLocalURI();
                final File imageFile = new File(imageFilename);
                if (imageFile.exists()) {
                    Bitmap b = MediaUtil.inflateDisplayImage(getContext(), imageURI, maxBounds[0],
                                maxBounds[1]);
                    if (b != null) {
                        ImageView mImageView = new ImageView(getContext());;
                        if (useResizingImageView()) {
                            mImageView = new ResizingImageView(getContext(), imageURI, bigImageURI);
                            mImageView.setAdjustViewBounds(true);
                            mImageView.setMaxWidth(maxBounds[0]);
                            mImageView.setMaxHeight(maxBounds[1]);
                        }
                        mImageView.setPadding(10, 10, 10, 10);
                        mImageView.setImageBitmap(b);
                        mImageView.setId(23423534);
                        mediaPane = mImageView;
                    } else if (errorMsg == null) {
                        // An error hasn't been logged and loading the image failed, so it's likely
                        // a bad file.
                        errorMsg = getContext().getString(R.string.file_invalid, imageFile);
                    }
                } else {
                    // An error hasn't been logged. We should have an image, but the file doesn't
                    // exist.
                    errorMsg = getContext().getString(R.string.file_missing, imageFile);
                }

                if (errorMsg != null) {
                    // errorMsg is only set when an error has occured
                    Log.e(t, errorMsg);
                    mMissingImage = new TextView(getContext());
                    mMissingImage.setText(errorMsg);
                    mMissingImage.setPadding(10, 10, 10, 10);
                    mMissingImage.setId(234873453);
                    mediaPane = mMissingImage;
                }
            } catch (InvalidReferenceException e) {
                Log.e(t, "image invalid reference exception");
                e.printStackTrace();
            }
        }

        if (mediaPane != null) {

            if (!textVisible) {
                this.addView(questionTextPane, questionTextPaneParams);
                if (mAudioButton != null) {
                    mediaPaneParams.addRule(RelativeLayout.LEFT_OF, mAudioButton.getId());
                    questionTextPane.addView(mediaPane, mediaPaneParams);
                }
                if (mVideoButton != null) {
                    mediaPaneParams.addRule(RelativeLayout.LEFT_OF, mVideoButton.getId());
                    questionTextPane.addView(mediaPane, mediaPaneParams);
                }
            } else {
                if (DeveloperPreferences.imageAboveTextEnabled()) {
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

    /**
     * Creates a video view for the provided URI or an error view elaborating why the video
     * couldn't be displayed.
     *
     * @param inlineVideoURI JavaRosa Reference URI
     * @param viewLayoutParams the layout params that will be applied to the view. Expect to be
     *                         mutated by this method
     */
    private View getInlineVideoView(String inlineVideoURI, RelativeLayout.LayoutParams viewLayoutParams) {
        String error = null;
        try {
            final String videoFilename = ReferenceManager._().DeriveReference(inlineVideoURI).getLocalURI();

            int[] maxBounds = getMaxCenterViewBounds();

            File videoFile = new File(videoFilename);
            if(!videoFile.exists()) {
                error = "No video file found at: " + videoFilename;
            } else {
                //NOTE: This has odd behavior when you have a text input on the screen
                //since clicking the video view to bring up controls has weird effects.
                //since we shotgun grab the focus for the input widget.

                final MediaController ctrl = new MediaController(this.getContext());

                VideoView videoView = new VideoView(this.getContext());
                videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        ctrl.show();
                    }
                });
                videoView.setVideoPath(videoFilename);
                videoView.setMediaController(ctrl);
                ctrl.setAnchorView(videoView);

                //These surprisingly get re-jiggered as soon as the video is loaded, so we
                //just want to give it the _max_ bounds, it'll pick the limiter and shrink
                //itself when it's ready.
                viewLayoutParams.width = maxBounds[0];
                viewLayoutParams.height = maxBounds[1];

                return videoView;
            }

        }catch(InvalidReferenceException ire) {
            Log.e(t, "invalid video reference exception");
            ire.printStackTrace();
            error = "Invalid reference: " + ire.getReferenceString();
        }

        if(error != null) {
            mMissingImage = new TextView(getContext());
            mMissingImage.setText(error);
            mMissingImage.setPadding(10, 10, 10, 10);
            mMissingImage.setId(234873453);
            return mMissingImage;
        } else {
            return null;
        }
    }

    private boolean useResizingImageView() {
        // only allow ResizingImageView to be used if not also using smart inflation
        return !CommCarePreferences.isSmartInflationEnabled() &&
                (ResizingImageView.resizeMethod.equals("full") ||
                        ResizingImageView.resizeMethod.equals("half") ||
                        ResizingImageView.resizeMethod.equals("width"));
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
        if(mView_Text != null){
            maxHeight = maxHeight - mView_Text.getHeight();
        } if(mVideoButton != null){
            maxHeight = maxHeight - mVideoButton.getHeight();
        } else if(mAudioButton != null){
            maxHeight = maxHeight - mAudioButton.getHeight();
        }

        // reduce by third for safety
        return new int[] {maxWidth, (2 * maxHeight)/3};
    }

    /**
     * This adds a divider at the bottom of this layout. Used to separate
     * fields in lists.
     */
    public void addDivider(ImageView v) {
        RelativeLayout.LayoutParams dividerParams =
            new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (mImageView != null) {
            dividerParams.addRule(RelativeLayout.BELOW, mImageView.getId());
        } else if (mMissingImage != null) {
            dividerParams.addRule(RelativeLayout.BELOW, mMissingImage.getId());
        } else if (mVideoButton != null) {
            dividerParams.addRule(RelativeLayout.BELOW, mVideoButton.getId());
        } else if (mAudioButton != null) {
            dividerParams.addRule(RelativeLayout.BELOW, mAudioButton.getId());
        } else if (mView_Text != null) {
            // No picture
            dividerParams.addRule(RelativeLayout.BELOW, mView_Text.getId());
        } else {
            Log.e(t, "Tried to add divider to uninitialized ATVWidget");
            return;
        }
        addView(v, dividerParams);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) {
            if (mAudioButton != null) {
                mAudioButton.endPlaying();
            }
        }
    }
}

package org.commcare.views.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.FileUtil;
import org.commcare.utils.StringUtils;
import org.commcare.utils.UriToFilePath;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.io.IOException;

/**
 * Widget that allows user to take pictures, sounds or video and add them to the form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class VideoWidget extends MediaWidget {
    private final static String TAG = VideoWidget.class.getSimpleName();

    public VideoWidget(Context context, final FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt, pic);
    }

    @Override
    protected void initializeButtons() {
        // setup capture button
        mCaptureButton = new Button(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_video),
                mAnswerFontsize,
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
                i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
                        Video.Media.EXTERNAL_CONTENT_URI.toString());
                try {
                    ((Activity)getContext()).startActivityForResult(i,
                            FormEntryActivity.AUDIO_VIDEO_FETCH);
                    pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found, "capture video"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // setup capture button
        mChooseButton = new Button(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_video),
                mAnswerFontsize,
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("video/*");
                try {
                    ((Activity)getContext()).startActivityForResult(i,
                            FormEntryActivity.AUDIO_VIDEO_FETCH);
                    pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found, "choose video "),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // setup play button
        mPlayButton = new Button(getContext());
        WidgetUtils.setupButton(mPlayButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.play_video),
                mAnswerFontsize,
                !mPrompt.isReadOnly());

        // on play, launch the appropriate viewer
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("android.intent.action.VIEW");
                File f = new File(mInstanceFolder + "/" + mBinaryName);
                i.setDataAndType(Uri.fromFile(f), "video/*");
                try {
                    getContext().startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found, "video video"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        String acq = mPrompt.getAppearanceHint();
        if (QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(acq)) {
            mChooseButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected String createFilePath(Object binaryUri){
        return UriToFilePath.getPathFromUri(CommCareApplication._(), (Uri)binaryUri);
    }

    @Override
    public void setBinaryData(Object binaryuri) {
        String binaryPath = getBinaryPathWithSizeCheck(binaryuri);
        if (binaryPath == null) {
            return;
        }
        File source = new File(binaryPath);

        String extension = binaryPath.substring(binaryPath.lastIndexOf("."));
        String destVideoPath = mInstanceFolder + "/" + System.currentTimeMillis() + extension;

        File newVideo = new File(destVideoPath);
        try {
            FileUtil.copyFile(source, newVideo);
        } catch (IOException e) {
            Log.e(TAG, "IOExeception while video audio");
            e.printStackTrace();
        }

        if (newVideo.exists()) {
            // Add the copy to the content provier
            ContentValues values = new ContentValues(6);
            values.put(Video.Media.TITLE, newVideo.getName());
            values.put(Video.Media.DISPLAY_NAME, newVideo.getName());
            values.put(Video.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(Video.Media.DATA, newVideo.getAbsolutePath());

            Uri VideoURI =
                    getContext().getContentResolver().insert(Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.i(TAG, "Inserting VIDEO returned uri = " + VideoURI.toString());
        } else {
            Log.e(TAG, "Inserting Video file FAILED");
        }

        mBinaryName = newVideo.getName();
    }
}

package org.commcare.views.widgets;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;

import org.commcare.activities.FormEntryActivity;
import org.commcare.logic.PendingCalloutInterface;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class MediaWidget extends QuestionWidget {
    private static final String TAG = MediaWidget.class.getSimpleName();

    protected Button mCaptureButton;
    protected Button mPlayButton;
    protected Button mChooseButton;

    protected final PendingCalloutInterface pendingCalloutInterface;

    protected String mBinaryName;
    protected final String mInstanceFolder;
    private int oversizedMediaSize;

    public MediaWidget(Context context, FormEntryPrompt p, PendingCalloutInterface pic) {
        super(context, p);

        this.pendingCalloutInterface = pic;

        mInstanceFolder =
                FormEntryActivity.mInstancePath.substring(0,
                        FormEntryActivity.mInstancePath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);
        initializeButtons();
        setupLayout();

        // retrieve answer from data model and update ui
        mBinaryName = mPrompt.getAnswerText();
        if (mBinaryName != null) {
            mPlayButton.setEnabled(true);
            File f = new File(mInstanceFolder + "/" + mBinaryName);
            checkFileSize(f);
        } else {
            checkForOversizedMedia(mPrompt.getAnswerValue());
            togglePlayButton(false);
        }
    }

    protected void reloadFile() {
        togglePlayButton(true);
        File f = new File(mInstanceFolder + mBinaryName);
        checkFileSize(f);
    }

    protected void togglePlayButton(boolean enabled) {
        mPlayButton.setEnabled(enabled);
    }

    protected abstract void initializeButtons();

    protected void setupLayout() {
        addView(mCaptureButton);
        addView(mChooseButton);
        addView(mPlayButton);
    }

    @Override
    public IAnswerData getAnswer() {
        if (mBinaryName != null) {
            return new StringData(mBinaryName);
        } else if (oversizedMediaSize > 0) {
            return new InvalidData("", new IntegerData(oversizedMediaSize));
        }
        return null;
    }

    protected String checkBinarySize(Object binaryuri) {
        String binaryPath = createFilePath(binaryuri);
        File source = new File(binaryPath);
        boolean isToLargeToUpload = checkFileSize(source);

        // when replacing an answer. remove the current media.
        if (mBinaryName != null) {
            deleteMedia();
        }

        if (isToLargeToUpload) {
            oversizedMediaSize = (int)source.length() / (1024 * 1024);
            return null;
        } else {
            oversizedMediaSize = -1;
            return binaryPath;
        }
    }

    private void deleteMedia() {
        // get the file path and delete the file
        File f = new File(mInstanceFolder + "/" + mBinaryName);
        if (!f.delete()) {
            Log.e(TAG, "Failed to delete " + f);
        }

        // clean up variables
        mBinaryName = null;
    }

    @Override
    public void clearAnswer() {
        deleteMedia();

        mPlayButton.setEnabled(false);
    }

    protected abstract String createFilePath(Object binaryUri);

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mCaptureButton.setOnLongClickListener(l);
        mChooseButton.setOnLongClickListener(l);
        mPlayButton.setOnLongClickListener(l);
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mCaptureButton.setOnLongClickListener(null);
        mChooseButton.setOnLongClickListener(null);
        mPlayButton.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mCaptureButton.cancelLongPress();
        mChooseButton.cancelLongPress();
        mPlayButton.cancelLongPress();
    }

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }
}

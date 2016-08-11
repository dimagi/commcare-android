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
 * Generic logic for capturing or choosing audio/video/image media
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class MediaWidget extends QuestionWidget {
    private static final String TAG = MediaWidget.class.getSimpleName();

    protected Button mCaptureButton;
    protected Button mPlayButton;
    protected Button mChooseButton;
    protected String mBinaryName;

    protected final PendingCalloutInterface pendingCalloutInterface;
    protected final String mInstanceFolder;

    private int oversizedMediaSize;

    public MediaWidget(Context context, FormEntryPrompt prompt,
                       PendingCalloutInterface pendingCalloutInterface) {
        super(context, prompt);

        this.pendingCalloutInterface = pendingCalloutInterface;

        mInstanceFolder =
                FormEntryActivity.mInstancePath.substring(0,
                        FormEntryActivity.mInstancePath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);
        initializeButtons();
        setupLayout();

        loadAnswerFromDataModel();
    }

    private void loadAnswerFromDataModel() {
        mBinaryName = mPrompt.getAnswerText();
        if (mBinaryName != null) {
            reloadFile();
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
        if (oversizedMediaSize > 0) {
            // media was too big to upload, set answer as invalid data to
            // allow showing the user a proper warning message.
            return new InvalidData("", new IntegerData(oversizedMediaSize));
        } else if (mBinaryName != null) {
            return new StringData(mBinaryName);
        }
        return null;
    }

    /**
     * @return resolved filepath or null if the target is too big to upload
     */
    protected String getBinaryPathWithSizeCheck(Object binaryURI) {
        String binaryPath = createFilePath(binaryURI);
        File source = new File(binaryPath);
        boolean isToLargeToUpload = checkFileSize(source);

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

    @Override
    public void clearAnswer() {
        deleteMedia();

        togglePlayButton(false);
    }

    private void deleteMedia() {
        File f = new File(mInstanceFolder + mBinaryName);
        if (!f.delete()) {
            Log.e(TAG, "Failed to delete " + f);
        }

        mBinaryName = null;
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

package org.commcare.views.widgets;

import android.content.Context;
import android.util.Log;

import org.commcare.activities.FormEntryActivity;
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
    protected String mBinaryName;
    protected final String mInstanceFolder;
    private int oversizedMediaSize;

    public MediaWidget(Context context, FormEntryPrompt p) {
        super(context, p);

        mInstanceFolder =
                FormEntryActivity.mInstancePath.substring(0,
                        FormEntryActivity.mInstancePath.lastIndexOf("/") + 1);

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
}

package org.commcare.android.tasks;

import org.commcare.resources.model.MissingMediaException;
import org.javarosa.core.util.SizeBoundVector;

public interface VerificationTaskListener {
    public void onFinished(SizeBoundVector<MissingMediaException> problems);
    public void updateVerifyProgress(int done, int pending);
    public void success();
    public void failUnknown();
}
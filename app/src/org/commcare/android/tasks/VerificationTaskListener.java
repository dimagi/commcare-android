package org.commcare.android.tasks;

import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.util.SizeBoundVector;

public interface VerificationTaskListener {
	public void onFinished(SizeBoundVector<UnresolvedResourceException> problems);
	public void updateVerifyProgress(int done, int pending);
	public void success();
	public void failUnknown();
}
package org.commcare.android.tasks;

import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.util.SizeBoundVector;

public interface VerificationTaskListener {
	public void onFinished(SizeBoundVector<UnresolvedResourceException> problems);
	public void updateProgress(int done, int pending, int phase);
	public void failMissingResources();
	public void success();
	public void failUnknown();
}
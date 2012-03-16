package org.commcare.android.tasks;

import org.commcare.resources.model.Resource;

public interface ResourceEngineListener {
	public void reportSuccess(boolean b);
	public void failMissingResource(Resource r);
	public void failBadReqs(int code);
	public void failUnknown();
	public void updateProgress(int done, int pending, int phase);
	public void failBadState();
}

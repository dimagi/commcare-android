package org.commcare.android.tasks;

import org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes;
import org.commcare.resources.model.Resource;

public interface ResourceEngineListener {
	public void reportSuccess(boolean b);
	public void failMissingResource(Resource r, ResourceEngineOutcomes statusmissing);
	public void failBadReqs(int code, String vReq, String vAvail, boolean majorIsProblem);
	public void failUnknown(ResourceEngineOutcomes statusfailunknown);
	public void updateProgress(int done, int pending, int phase);
	public void failBadState(ResourceEngineOutcomes statusfailstate);
}
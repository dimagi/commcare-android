package org.commcare.android.tasks;

import org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes;
import org.commcare.resources.model.UnresolvedResourceException;

public interface ResourceEngineListener {
    public void reportSuccess(boolean b);
    public void failMissingResource(UnresolvedResourceException ure, ResourceEngineOutcomes statusmissing);
    public void failBadReqs(int code, String vReq, String vAvail, boolean majorIsProblem);
    public void failUnknown(ResourceEngineOutcomes statusfailunknown);
    public void updateProgress(int done, int pending, int phase);
    public void failWithNotification(ResourceEngineOutcomes statusfailstate);
}
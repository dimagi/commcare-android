package org.commcare.android.tasks;

import org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes;
import org.commcare.resources.model.UnresolvedResourceException;

public interface ResourceEngineListener {
    void reportSuccess(boolean b);
    void failMissingResource(UnresolvedResourceException ure, ResourceEngineOutcomes statusmissing);
    void failBadReqs(int code, String vReq, String vAvail, boolean majorIsProblem);
    void failUnknown(ResourceEngineOutcomes statusfailunknown);
    void updateResourceProgress(int done, int pending, int phase);
    void failWithNotification(ResourceEngineOutcomes statusfailstate);
}

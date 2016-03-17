package org.commcare.tasks;

import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.resources.model.UnresolvedResourceException;

public interface ResourceEngineListener {
    void reportSuccess(boolean b);

    void failMissingResource(UnresolvedResourceException ure, AppInstallStatus statusmissing);

    void failBadReqs(int code, String vReq, String vAvail, boolean majorIsProblem);

    void failUnknown(AppInstallStatus statusfailunknown);

    void updateResourceProgress(int done, int pending, int phase);

    void failWithNotification(AppInstallStatus statusfailstate);
}

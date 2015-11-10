package org.commcare.android.util;

import org.commcare.android.cases.AndroidCaseInstanceTreeElement;
import org.commcare.android.database.AndroidSandbox;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.ExternalDataInstance;

/**
 * @author ctsims
 */
public class AndroidInstanceInitializer extends CommCareInstanceInitializer {

    public AndroidInstanceInitializer(CommCareSession session) {
        super(session, new AndroidSandbox(CommCareApplication._()), CommCareApplication._().getCommCarePlatform());
    }

    @Override
    protected AbstractTreeElement setupCaseData(ExternalDataInstance instance) {
        if (casebase == null) {
            SqlStorage<ACase> storage = (SqlStorage<ACase>)mSandbox.getCaseStorage();
            casebase = new AndroidCaseInstanceTreeElement(instance.getBase(), storage, false);
        } else {
            //re-use the existing model if it exists.
            casebase.rebase(instance.getBase());
        }
        instance.setCacheHost((AndroidCaseInstanceTreeElement)casebase);
        return casebase;
    }

    @Override
    public String getVersionString() {
        return CommCareApplication._().getCurrentVersionString();
    }

    @Override
    public String getDeviceId() {
        return CommCareApplication._().getPhoneId();
    }
}

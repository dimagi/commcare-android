/**
 * 
 */
package org.commcare.android.util;

import org.commcare.android.database.AndroidSandbox;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;

/**
 * @author ctsims
 *
 */
public class AndroidInstanceInitializer extends CommCareInstanceInitializer {

    public AndroidInstanceInitializer(CommCareSession session) {
        super(session, new AndroidSandbox(CommCareApplication._()), CommCareApplication._().getCommCarePlatform());
    }

    @Override
    public String getVersionString(){
        return CommCareApplication._().getCurrentVersionString();
    }

    @Override
    public String getDeviceId(){
        return CommCareApplication._().getPhoneId();
    }
}

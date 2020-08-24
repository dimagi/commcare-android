package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.javarosa.core.util.PropertyUtils;

public class CczUtils {

    // Returns path to extract an ccz
    public static String getCczTargetPath() {
        return CommCareApplication.instance().getAndroidFsTemp() + PropertyUtils.genUUID();
    }
}

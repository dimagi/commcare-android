package org.commcare.models.legacy;

import android.content.Context;

import org.commcare.util.LogTypes;
import org.commcare.utils.GlobalConstants;
import org.javarosa.core.services.Logger;


/**
 * @author ctsims
 */
public class LegacyInstallUtils {

    //Check to see if the legacy database exists on this system
    public static boolean checkForLegacyInstall(Context c) {
        if (c.getDatabasePath(GlobalConstants.CC_DB_NAME).exists()) {
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Legacy install detected");
            return true;
        }
        return false;
    }
}

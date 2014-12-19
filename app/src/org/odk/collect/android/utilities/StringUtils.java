/**
 * 
 */
package org.odk.collect.android.utilities;

import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import android.content.Context;

/**
 * @author ctsims
 *
 */
public class StringUtils {

    
    public static String getStringRobust(Context c, int resId) {
        return getStringRobust(c, resId, "");
    }

    public static String getStringRobust(Context c, int resId, String args) {
        String resourceName = c.getResources().getResourceEntryName(resId);
        try {
            return Localization.get("odk_" + resourceName, new String[] {args});
        } catch(NoLocalizedTextException e) {
            return c.getString(resId, args);
        }
    }
}

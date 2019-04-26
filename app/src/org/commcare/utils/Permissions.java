package org.commcare.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Acquire Android permissions needed by CommCare.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class Permissions {
    public final static int ALL_PERMISSIONS_REQUEST = 1;

    /**
     * Ask for Android permissions needed by the app all at once.  This goes
     * against what is suggested by Android, but asking for permissions on
     * demand will confuse the hell out our users.
     *
     * @param activity        Used to show user dialog with rationale behind
     *                        permission requests
     * @param permRequester   performs user-facing permission request system calls
     * @param permRequestCode make the permission request using this request code
     * @return Was the user asked for permissions?
     */
    public static boolean acquireAllAppPermissions(Activity activity,
                                                   RuntimePermissionRequester permRequester,
                                                   int permRequestCode) {
        String[] permissions = getAppPermissions();

        if (missingAppPermission(activity, permissions)) {
            if (shouldShowPermissionRationale(activity, permissions)) {
                CommCareAlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(activity, permRequester,
                                permRequestCode,
                                Localization.get("permission.all.title"),
                                Localization.get("permission.all.message"));
                dialog.showNonPersistentDialog();
            } else {
                permRequester.requestNeededPermissions(permRequestCode);
            }
            return true;
        } else {
            return false;
        }
    }

    private static boolean missingAppPermission(Activity activity,
                                                String[] permissions) {
        for (String perm : permissions) {
            if (missingAppPermission(activity, perm)) {
                return true;
            }
        }
        return false;
    }

    public static boolean missingAppPermission(Activity activity,
                                               String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED;
    }

    private static boolean shouldShowPermissionRationale(Activity activity,
                                                         String[] permissions) {
        for (String perm : permissions) {
            if (shouldShowPermissionRationale(activity, perm)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldShowPermissionRationale(Activity activity,
                                                        String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    /**
     * @return Permissions needed for _normal_ CommCare functionality
     */
    public static String[] getAppPermissions() {
        ArrayList<String> neededPermissions = new ArrayList<>();
        Collections.addAll(neededPermissions, getRequiredPerms());
        neededPermissions.add(Manifest.permission.READ_PHONE_STATE);
        neededPermissions.add(Manifest.permission.CALL_PHONE);
        neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        neededPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        neededPermissions.add(Manifest.permission.RECORD_AUDIO);
        return neededPermissions.toArray(new String[neededPermissions.size()]);
    }

    /**
     * @return Minimal set of permissions needed for CommCare to function
     */
    public static String[] getRequiredPerms() {
        ArrayList<String> requiredPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            // exclude READ_EXTERNAL_STORAGE which isn't compat. w/ API < 16
            requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return requiredPermissions.toArray(new String[requiredPermissions.size()]);
    }
}

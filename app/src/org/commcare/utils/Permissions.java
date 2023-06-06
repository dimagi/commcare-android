package org.commcare.utils;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.javarosa.core.services.locale.Localization;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
    public static boolean acquireAllAppPermissions(AppCompatActivity activity,
                                                   RuntimePermissionRequester permRequester,
                                                   int permRequestCode) {
        boolean wasUserAskedForPermissions = false;
        String[] runtimePermissions = getRuntimeAppPermissions();
        if (missingAppPermission(activity, runtimePermissions)) {
            acquireRuntimeAppPermissions(activity, runtimePermissions, permRequester,
                    permRequestCode);
            wasUserAskedForPermissions = true;
        }

        String[] specialPermissions = getSpecialAppPermissions();
        if (missingAppPermission(activity, specialPermissions)) {
            acquireSpecialAppPermissions(activity, specialPermissions);
            wasUserAskedForPermissions = true;
        }
        return wasUserAskedForPermissions;
    }

    public static boolean missingAppPermission(AppCompatActivity activity,
                                               String[] permissions) {
        for (String perm : permissions) {
            if (missingAppPermission(activity, perm)) {
                return true;
            }
        }
        return false;
    }

    public static boolean missingAppPermission(AppCompatActivity activity,
                                               String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED;
    }

    public static boolean shouldShowPermissionRationale(AppCompatActivity activity,
                                                        String[] permissions) {
        for (String perm : permissions) {
            if (shouldShowPermissionRationale(activity, perm)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldShowPermissionRationale(AppCompatActivity activity,
                                                        String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    /**
     * @return Permissions needed for _normal_ CommCare functionality
     */
    public static String[] getRuntimeAppPermissions() {
        return new String[]{Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
        };
    }

    /**
     * @return Minimal set of permissions needed for CommCare to function
     */
    public static String[] getRequiredPerms() {
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }

    private static String[] getSpecialAppPermissions() {
    }

    private static void acquireSpecialAppPermissions(AppCompatActivity activity,
                                                     String[] specialPermissions) {
    }

    private static void acquireRuntimeAppPermissions(AppCompatActivity activity,
                                                     String[] runtimePermissions,
                                                     RuntimePermissionRequester permRequester,
                                                     int permRequestCode) {
        if (shouldShowPermissionRationale(activity, runtimePermissions)) {
            CommCareAlertDialog dialog =
                    DialogCreationHelpers.buildPermissionRequestDialog(activity, permRequester,
                            permRequestCode,
                            Localization.get("permission.all.title"),
                            Localization.get("permission.all.message"));
            dialog.showNonPersistentDialog();
        } else {
            permRequester.requestNeededPermissions(permRequestCode);
        }
    }
}

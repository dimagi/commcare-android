package org.commcare.utils;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import org.commcare.dalvik.BuildConfig;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.javarosa.core.services.locale.Localization;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Acquire Android permissions needed by CommCare.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class Permissions {
    public final static int ALL_PERMISSIONS_REQUEST = 1;
    public static final String COMMCARE_CASE_READ_PERMISSION = BuildConfig.APPLICATION_ID + ".provider.cases.read";

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
        String[] permissions = getAppPermissions();

        if (missingAppPermission(activity, permissions)) {
            if (shouldShowPermissionRationale(activity, permissions)) {
                CommCareAlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(activity, permRequester,
                                permRequestCode,
                                Localization.get("permission.all.title"),
                                Localization.get("permission.all.message"));
                dialog.showNonPersistentDialog(activity);
            } else {
                permRequester.requestNeededPermissions(permRequestCode);
            }
            return true;
        } else {
            return false;
        }
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
    public static String[] getAppPermissions() {
        List<String> appPermissions = new ArrayList<>(Arrays.asList(new String[]{
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO}));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appPermissions.addAll(Arrays.asList(new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS}));
        } else {
            appPermissions.addAll(Arrays.asList(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}));
        }
        return appPermissions.toArray(new String[]{});
    }

    /**
     * @return Minimal set of permissions needed for CommCare to function
     */
    public static String[] getRequiredPerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO};
        }
        return new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }
}

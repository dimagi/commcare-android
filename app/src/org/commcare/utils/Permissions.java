package org.commcare.utils;

import android.content.Context;

import android.Manifest;
import android.app.AlarmManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.commcare.activities.CommCareActivity;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import java.util.ArrayList;
import java.util.List;

/**
 * Acquire Android permissions needed by CommCare.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class Permissions {
    public final static int ALL_PERMISSIONS_REQUEST = 1;
    public static final int SCHEDULE_EXACT_ALARM_PERMISSION_REQUEST = 2;

    // This flag is to be used when requesting Special access permissions and prevent triggering
    // the request when the configuration of the device changes during the process. It should be
    // switched off on the onActivityResult method of the requesting Activity
    public static boolean requestingSpecialAppPermission;

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
        int permCheckResult = PermissionChecker.checkSelfPermission(activity, permission);
        return  (permCheckResult == PermissionChecker.PERMISSION_DENIED) ||
                (permCheckResult == PermissionChecker.PERMISSION_DENIED_APP_OP);
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

    // More Special Permissions can be added to the list when necessary
    private static String[] getSpecialAppPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            permissions.add(Manifest.permission.SCHEDULE_EXACT_ALARM);
        return permissions.toArray(new String[permissions.size()]);
    }

    private static void acquireSpecialAppPermissions(AppCompatActivity activity,
                                                     String[] specialPermissions) {
        for (String specialPerm : specialPermissions) {
            if (shouldRequestSpecialPermission(activity, specialPerm)){
                requestSpecialPermission(activity, specialPerm);
            }
        }
    }

    private static void requestSpecialPermission(AppCompatActivity activity, String specialPerm) {
        StandardAlertDialog d = new StandardAlertDialog(activity, Localization.get(getSpecialPermissionDialogTitle(specialPerm)),
                Localization.get(getSpecialPermissionRequestDialogMessage(specialPerm)));
        DialogInterface.OnClickListener listener = (dialog, which) -> {
            if (which == AlertDialog.BUTTON_POSITIVE) {
                switch(specialPerm) {
                    case Manifest.permission.SCHEDULE_EXACT_ALARM:
                        startAlarmsAndRemindersActivity(activity);
                        break;
                    default:
                        throw new RuntimeException("Invalid special permission " + specialPerm);
                }
            }
            else
                communicateFeatureDegradation(activity, specialPerm);
            dialog.dismiss();
        };
        d.setPositiveButton(Localization.get("permission.acquire.dialog.allow"), listener);
        d.setNegativeButton(Localization.get("permission.acquire.dialog.deny"), listener);
        d.showNonPersistentDialog();
    }

    public static void communicateFeatureDegradation(AppCompatActivity activity, String specialPerm) {
        // Any other measures should be registered in the switch below
        switch(specialPerm){
            case Manifest.permission.SCHEDULE_EXACT_ALARM:
                // Don't check this permission again, the user will have to go to Apps -> Special access
                // to grant it
                HiddenPreferences.setCheckScheduleExactAlarmPermission(false);
                break;
            default:
                throw new RuntimeException("Invalid special permission " + specialPerm);
        }
        StandardAlertDialog d = StandardAlertDialog.getBasicAlertDialog(activity,
                Localization.get(getSpecialPermissionDialogTitle(specialPerm)),
                Localization.get(getSpecialPermissionDenialDialogMessage(specialPerm)),null);
        d.showNonPersistentDialog();
    }

    private static String getSpecialPermissionDenialDialogMessage(String specialPerm) {
        switch(specialPerm){
            case Manifest.permission.SCHEDULE_EXACT_ALARM:
                return "permission.schedule.exact.alarm.denied";
            default:
                throw new RuntimeException("Invalid special permission " + specialPerm);
        }
    }

    private static String getSpecialPermissionRequestDialogMessage(String specialPerm) {
        switch(specialPerm){
            case Manifest.permission.SCHEDULE_EXACT_ALARM:
                return "permission.schedule.exact.alarm.message";
            default:
                throw new RuntimeException("Invalid special permission " + specialPerm);
        }
    }

    private static String getSpecialPermissionDialogTitle(String specialPerm) {
        switch(specialPerm){
            case Manifest.permission.SCHEDULE_EXACT_ALARM:
                return "permission.schedule.exact.alarm.title";
            default:
                throw new RuntimeException("Invalid special permission " + specialPerm);
        }
    }

    // TODO: In addition to scrolling to where CommCare app is, we should highlight the item
    // https://stackoverflow.com/questions/62979001/highlighting-a-menu-item-in-system-settings
    private static void startAlarmsAndRemindersActivity(AppCompatActivity activity) {
        requestingSpecialAppPermission = true;
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);

        intent.setData(Uri.parse("package:"+activity.getPackageName()));
        activity.startActivityForResult(intent, SCHEDULE_EXACT_ALARM_PERMISSION_REQUEST);
    }

    private static boolean shouldRequestSpecialPermission(AppCompatActivity activity, String specialPerm) {
        if (!requestingSpecialAppPermission) {
            switch (specialPerm) {
                case Manifest.permission.SCHEDULE_EXACT_ALARM:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        return !(((AlarmManager) activity.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms())
                                && HiddenPreferences.getCheckScheduleExactAlarmPermission();
                    }
                    break;
                default:
                    throw new RuntimeException("Invalid special permission " + specialPerm);
            }
        }
        return false;
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

package org.commcare.android.util;

import android.content.SharedPreferences;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.services.Logger;

import java.security.cert.CertificateException;
import java.util.Date;

import javax.net.ssl.SSLHandshakeException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class InstallAndUpdateUtils {
    public static void recordUpdateAttempt(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, new Date().getTime());
        editor.commit();
    }

    public static void updateProfileRef(SharedPreferences prefs, String authRef, String profileRef) {
        SharedPreferences.Editor edit = prefs.edit();
        if (authRef != null) {
            edit.putString(ResourceEngineTask.DEFAULT_APP_SERVER, authRef);
        } else {
            edit.putString(ResourceEngineTask.DEFAULT_APP_SERVER, profileRef);
        }
        edit.commit();
    }

    public static boolean isBadCertificateError(UnresolvedResourceException e) {
        Throwable mExceptionCause = e.getCause();

        if (mExceptionCause instanceof SSLHandshakeException) {
            Throwable mSecondExceptionCause = mExceptionCause.getCause();
            if (mSecondExceptionCause instanceof CertificateException) {
                return true;
            }
        }
        return false;
    }

    public static void logInstallError(Exception e, String logMessage) {
        e.printStackTrace();

        Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                logMessage + e.getMessage());
    }
}

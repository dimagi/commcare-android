package org.commcare.android.analytics;

import android.util.Base64;

import org.commcare.resources.model.InstallStatListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ResourceDownloadStats implements InstallStatListener, Serializable {
    private static final String TAG = ResourceDownloadStats.class.getSimpleName();

    private Hashtable<String, InstallAttempts<String>> installStats;
    private long startInstallTime;
    private int restartCount = 0;
    private static long TWO_WEEKS_IN_MS = 1000 * 60 * 60 * 24 * 24;

    public ResourceDownloadStats() {
        startInstallTime = new Date().getTime();
        installStats = new Hashtable<>();
    }

    public void incRestartCount() {
        restartCount++;
    }

    public boolean isUpgradeStale() {
        long currentTime = new Date().getTime();
        return (restartCount > 3 || (currentTime - startInstallTime) > TWO_WEEKS_IN_MS);
    }

    @Override
    public void recordResourceInstallFailure(String resourceName,
                                             String errorMsg) {
        InstallAttempts<String> attempts = installStats.get(resourceName);
        if (attempts == null) {
            attempts = new InstallAttempts<>(resourceName);
            installStats.put(resourceName, attempts);
        }
        attempts.add(errorMsg);
    }

    @Override
    public void recordResourceInstallSuccess(String resourceName) {
        InstallAttempts<String> attempts = installStats.get(resourceName);
        if (attempts == null) {
            attempts = new InstallAttempts<>(resourceName);
            installStats.put(resourceName, attempts);
        }
        attempts.wasSuccessful = true;
    }

    public static Object deserialize(String s) throws IOException,
            ClassNotFoundException {
        byte[] data = Base64.decode(s, Base64.DEFAULT);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public static String serialize(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }
}

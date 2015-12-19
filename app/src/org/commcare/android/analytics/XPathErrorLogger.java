package org.commcare.android.analytics;

import org.commcare.android.database.SqlStorage;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.xpath.XPathException;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public enum XPathErrorLogger {
    /**
     * Singleton instance
     */
    INSTANCE;

    private static SqlStorage<XPathErrorEntry> logStorage;

    public static void registerStorage(SqlStorage<XPathErrorEntry> storage) {
        logStorage = storage;
    }

    public void logErrorToCurrentApp(String source, String message) {
        logStorage.write(new XPathErrorEntry(source, message));
    }

    public void logErrorToCurrentApp(XPathException exception) {
        logStorage.write(new XPathErrorEntry(exception.getSource(), exception.getMessage()));
    }
}

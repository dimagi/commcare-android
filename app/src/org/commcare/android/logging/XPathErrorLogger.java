package org.commcare.android.logging;

import org.commcare.android.database.SqlStorage;
import org.javarosa.xpath.XPathException;

/**
 * Log xpath errors such that they're associated w/ cc app versions
 *
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

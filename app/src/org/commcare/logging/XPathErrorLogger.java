package org.commcare.logging;

import org.commcare.models.database.SqlStorage;
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
        if (logStorage != null) {
            logStorage.write(new XPathErrorEntry(source, message));
        }
    }

    public void logErrorToCurrentApp(String message) {
        if (logStorage != null) {
            logStorage.write(new XPathErrorEntry(null, message));
        }
    }

    public void logErrorToCurrentApp(XPathException exception) {
        if (logStorage != null) {
            logStorage.write(new XPathErrorEntry(exception.getSource(), exception.getMessage()));
        }
    }
}

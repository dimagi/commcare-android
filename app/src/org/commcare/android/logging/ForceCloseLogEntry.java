package org.commcare.android.logging;

import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.util.LogTypes;
import java.util.Date;

/**
 * Log entry for force closes, capturing the app build number, android version, device model,
 * readable session string, and serialized session string.
 *
 * @author Aliza Stone
 */
public class ForceCloseLogEntry extends AndroidLogEntry {

    public static final String STORAGE_KEY = "forcecloses";

    /**
     * Serialization only
     */
    public ForceCloseLogEntry() {

    }

    public ForceCloseLogEntry(String stackTrace) {
        super(LogTypes.TYPE_FORCECLOSE, stackTrace, new Date());
    }

}

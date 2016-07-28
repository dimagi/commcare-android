package org.commcare.logging;

import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.log.LogEntry;
import org.javarosa.core.log.StreamLogSerializer;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.util.SortedIntSet;

import java.util.Hashtable;

/**
 * Implementation of a log purger shared by all Android log serializers
 *
 * @author Aliza Stone
 */
public class AndroidLogPurger<T extends AndroidLogEntry> implements StreamLogSerializer.Purger {

    private final SqlStorage<T> logStorage;

    public AndroidLogPurger(SqlStorage<T> logStorage) {
        this.logStorage = logStorage;
    }

    @Override
    public void purge(final SortedIntSet IDs) {
        logStorage.removeAll(new EntityFilter<LogEntry>() {
            @Override
            public int preFilter(int id, Hashtable<String, Object> metaData) {
                return IDs.contains(id) ? PREFILTER_INCLUDE : PREFILTER_EXCLUDE;
            }

            @Override
            public boolean matches(LogEntry e) {
                throw new RuntimeException("can't happen");
            }
        });
    }
}

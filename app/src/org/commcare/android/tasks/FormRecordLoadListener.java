package org.commcare.android.tasks;

import org.commcare.models.database.user.models.FormRecord;

/**
 * Listener methods that are used to keep track of loading progress of form
 * records and their query-able data.
 *
 * @author ctsims
 */
public interface FormRecordLoadListener {

    /**
     * Called every time a single FormRecord has been processed by
     * FormRecordLoaderTask.
     *
     * @param record   The form record that was processed.
     * @param isLoaded Currently unused. TODO PLM: figure out how to use this
     *                 or remove it.
     */
    void notifyPriorityLoaded(FormRecord record, boolean isLoaded);

    /**
     * Called every time FormRecordLoaderTask finishes loading.
     */
    void notifyLoaded();
}

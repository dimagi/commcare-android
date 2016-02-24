package org.odk.collect.android.listeners;

import org.odk.collect.android.tasks.SaveToDiskTask;

/**
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public interface FormSavedListener {

    /**
     * Callback to be run after a form has been saved.
     */
    void savingComplete(SaveToDiskTask.SaveStatus formSaveStatus, String errorMessage);
}

package org.odk.collect.android.listeners;

/**
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public interface FormSavedListener {

    /**
     * Callback to be run after a form has been saved.
     *
     * @param saveStatus return status of form save, defined in SaveToDiskTask
     * @param headless is this thread running without a GUI?
     */
    void savingComplete(int saveStatus, boolean headless);
}

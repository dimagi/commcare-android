package org.commcare.dalvik.dialogs;

/**
 * @author amstone326
 */
public interface DialogController {
    /** Should call generateProgressDialog to obtain an instance of a dialog
     * for the given taskId, and then call show on that dialog fragment */
    void showProgressDialog(int taskId);

    /** Should dismiss the current dialog fragment */
    void dismissProgressDialog();

    /** Return the dialog that is currently showing, or null
     * if none exists */
    CustomProgressDialog getCurrentDialog();

    /** Update the current dialog's message to the new text */
    void updateProgress(String updateText, int taskId);

    /** Update the current dialog's progress bar */
    void updateProgressBar(int progress, int max, int taskId);

    /** Create an instance of CustomProgressDialog specific to the activity
     *  implementing this method -- this method should be implemented lower down
     *  in the activity hierarchy, in one of CommCareActivity's subclasses,
     *  while the other methods can be handled entirely by CommCareActivity */
    CustomProgressDialog generateProgressDialog(int taskId);
}

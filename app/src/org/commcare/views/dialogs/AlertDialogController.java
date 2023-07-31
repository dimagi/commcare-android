package org.commcare.views.dialogs;

/**
 * @author amstone326
 */
public interface AlertDialogController {
    /**
     * Show the alert dialog provided by the given AlertDialogFactory
     */
    void showAlertDialog(CommCareAlertDialog dialog);

    /**
     * Dismiss the current alert dialog
     */
    void dismissAlertDialog();
}

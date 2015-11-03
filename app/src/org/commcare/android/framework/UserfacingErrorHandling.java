package org.commcare.android.framework;

import android.app.Activity;
import android.content.DialogInterface;

import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.dialogs.AlertDialogFactory;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class UserfacingErrorHandling {

    /**
     * Pop up a semi-friendly error dialog rather than crashing outright.
     *
     * @param activity   Activity to which to attach the dialog.
     * @param shouldExit If true, cancel activity when user exits dialog.
     */
    public static void createErrorDialog(final CommCareActivity activity, String errorMsg,
                                         final boolean shouldExit) {
        String title = StringUtils.getStringRobust(activity, org.commcare.dalvik.R.string.error_occured);

        AlertDialogFactory factory = new AlertDialogFactory(activity, title, errorMsg);
        factory.setIcon(android.R.drawable.ic_dialog_info);

        DialogInterface.OnCancelListener cancelListener =
                new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (shouldExit) {
                            activity.setResult(Activity.RESULT_CANCELED);
                            activity.finish();
                        }
                    }
                };
        factory.setOnCancelListener(cancelListener);

        CharSequence buttonDisplayText =
                StringUtils.getStringSpannableRobust(activity, org.commcare.dalvik.R.string.ok);
        DialogInterface.OnClickListener buttonListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        if (shouldExit) {
                            activity.setResult(Activity.RESULT_CANCELED);
                            activity.finish();
                        }
                    }
                };
        factory.setPositiveButton(buttonDisplayText, buttonListener);

        activity.showAlertDialog(factory);
    }
}

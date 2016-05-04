package org.commcare.views;

import android.app.Activity;
import android.content.DialogInterface;

import org.commcare.activities.CommCareActivity;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.xpath.XPathException;

/**
 * Dialog logic for showing errors to user.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class UserfacingErrorHandling {

    public static void logErrorAndShowDialog(final CommCareActivity activity,
                                             XPathException exception,
                                             final boolean shouldExit) {
        XPathErrorLogger.INSTANCE.logErrorToCurrentApp(exception);

        final String errorMsg = exception.getMessage();
        createErrorDialog(activity, errorMsg, shouldExit);
    }

    /**
     * Pop up a semi-friendly error dialog rather than crashing outright.
     *
     * @param activity   Activity to which to attach the dialog.
     * @param shouldExit If true, cancel activity when user exits dialog.
     */
    public static void createErrorDialog(final CommCareActivity activity, String errorMsg,
                                         final boolean shouldExit) {
        String title = StringUtils.getStringRobust(activity, org.commcare.dalvik.R.string.error_occured);
        createErrorDialog(activity, errorMsg, title, shouldExit);
    }

    public static void createErrorDialog(final CommCareActivity activity, String errorMsg,
                                         String dialogTitle, final boolean shouldExit) {
        StandardAlertDialog factory = new StandardAlertDialog(activity, dialogTitle, errorMsg);
        factory.setIcon(android.R.drawable.ic_dialog_info);

        DialogInterface.OnCancelListener cancelListener =
                new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (shouldExit) {
                            activity.setResult(Activity.RESULT_CANCELED);
                            activity.finish();
                        }
                        dialog.dismiss();
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
                        dialog.dismiss();
                    }
                };
        factory.setPositiveButton(buttonDisplayText, buttonListener);

        activity.showAlertDialog(factory);
    }
}

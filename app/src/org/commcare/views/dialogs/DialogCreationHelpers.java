package org.commcare.views.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.utils.MarkupUtil;
import org.javarosa.core.services.locale.Localization;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class DialogCreationHelpers {
    public static AlertDialog buildAboutCommCareDialog(Activity activity) {
        final String commcareVersion = CommCareApplication._().getCurrentVersionString();

        LayoutInflater li = LayoutInflater.from(activity);
        View view = li.inflate(R.layout.scrolling_info_dialog, null);

        TextView titleView = (TextView) view.findViewById(R.id.dialog_title_text);
        titleView.setText(activity.getString(R.string.about_cc));

        TextView aboutText = (TextView)view.findViewById(R.id.dialog_text);
        String msg = activity.getString(R.string.aboutdialog, commcareVersion);
        Spannable markdownText = MarkupUtil.returnMarkdown(activity, msg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            aboutText.setText(markdownText);
        } else {
            aboutText.setText(markdownText.toString());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(view);
        builder.setPositiveButton(Localization.get("dialog.ok"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        return builder.create();
    }

    /**
     * Build dialog that tells user why they should authorize a given
     * permission. Pressing positive button launches the system's permission
     * request dialgo
     *
     * @param permRequester interface for launching system permission request
     *                      dialog
     */
    public static AlertDialog buildPermissionRequestDialog(Activity activity,
                                                           final RuntimePermissionRequester permRequester,
                                                           final int requestCode,
                                                           String title,
                                                           String body) {
        View view = LayoutInflater.from(activity).inflate(R.layout.scrolling_info_dialog, null);

        TextView bodyText = (TextView)view.findViewById(R.id.dialog_text);
        bodyText.setText(body);

        TextView titleText = (TextView) view.findViewById(R.id.dialog_title_text);
        titleText.setText(title);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(false);
        builder.setView(view);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                permRequester.requestNeededPermissions(requestCode);
                dialog.dismiss();
            }
        });
        return builder.create();
    }
}

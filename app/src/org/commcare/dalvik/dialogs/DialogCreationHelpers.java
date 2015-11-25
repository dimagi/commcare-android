package org.commcare.dalvik.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.android.util.MarkupUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class DialogCreationHelpers {
    public static AlertDialog buildAboutCommCareDialog(Activity activity) {
        final String commcareVersion = CommCareApplication._().getCurrentVersionString();

        LayoutInflater li = LayoutInflater.from(activity);
        // TODO PLM: remove scroll file
        //View view = li.inflate(R.layout.scrolling_info_dialog, null);
        View view = li.inflate(R.layout.about_commcare_dialog, null);

        TextView titleView = (TextView) view.findViewById(R.id.dialog_title).findViewById(R.id.dialog_title_text);
        titleView.setText(activity.getString(R.string.about_cc));

        TextView aboutText = (TextView)view.findViewById(R.id.about_commcare_text);
        String msg = activity.getString(R.string.aboutdialog, commcareVersion);
        Spannable markdownText = MarkupUtil.returnMarkdown(activity, msg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            aboutText.setText(markdownText);
        } else {
            aboutText.setText(markdownText.toString());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(view);
        return builder.create();
    }

    /**
     * Build dialog that tells user why they should authorize a given
     * permission. Pressing positive button launches the system's permission
     * request dialgo
     *
     * @param permRequester interface for launching system permission request
     * dialog
     */
    public static AlertDialog buildPermissionRequestDialog(Activity activity,
                                                           final RuntimePermissionRequester permRequester,
                                                           String title,
                                                           String body) {
        LayoutInflater li = LayoutInflater.from(activity);
        View view = li.inflate(R.layout.about_commcare_dialog, null);
        TextView aboutText = (TextView)view.findViewById(R.id.about_commcare_text);

        aboutText.setText(body);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setView(view);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                permRequester.requestNeededPermissions();
                dialog.dismiss();
            }
        });
        builder.setCancelable(false);

        return builder.create();
    }
}

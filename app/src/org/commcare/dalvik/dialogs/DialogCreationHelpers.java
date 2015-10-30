package org.commcare.dalvik.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

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
        View view = li.inflate(R.layout.about_commcare_dialog, null);

        TextView titleView = (TextView) view.findViewById(R.id.dialog_title).findViewById(R.id.dialog_title_text);
        titleView.setText("About CommCare");

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
}

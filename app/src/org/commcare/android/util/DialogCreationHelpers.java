package org.commcare.android.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class DialogCreationHelpers {
    public static AlertDialog buildAboutCommCareDialog(Activity activity) {
        final String commcareVersion = CommCareApplication._().getCurrentVersionString();

        LayoutInflater li = LayoutInflater.from(activity);
        View view = li.inflate(R.layout.formatted_about_dialog, null);
        TextView label=(TextView)view.findViewById(R.id.about_commcare_text);

        String msg = activity.getString(R.string.aboutdialog, commcareVersion);
        Spannable markdownText = MarkupUtil.returnMarkdown(activity, msg);
        label.setText(markdownText);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("About CommCare");
        builder.setView(view);

        return builder.create();
    }
}

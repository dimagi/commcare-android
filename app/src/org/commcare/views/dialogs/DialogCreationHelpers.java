package org.commcare.views.dialogs;

import android.content.Context;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.commcare.AppUtils;
import org.commcare.dalvik.R;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.utils.MarkupUtil;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.locale.Localization;

import androidx.appcompat.app.AppCompatActivity;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class DialogCreationHelpers {

    public static CommCareAlertDialog buildAboutCommCareDialog(AppCompatActivity activity) {
        return buildAboutCommCareDialog(activity, true);
    }

    public static CommCareAlertDialog buildAboutCommCareDialog(AppCompatActivity activity, boolean showAppInfo) {
        LayoutInflater li = LayoutInflater.from(activity);
        View view = li.inflate(R.layout.scrolling_info_dialog, null);
        TextView titleView = view.findViewById(R.id.dialog_title_text);
        titleView.setText(activity.getString(R.string.about_cc));
        Spannable markdownText = buildAboutMessage(activity, showAppInfo);
        TextView aboutText = view.findViewById(R.id.dialog_text);
        aboutText.setText(markdownText);

        CustomViewAlertDialog dialog = new CustomViewAlertDialog(view);
        dialog.setPositiveButton(Localization.get("dialog.ok"), (dialog1, which) -> dialog1.dismiss());

        return dialog;
    }

    private static Spannable buildAboutMessage(Context context, boolean showAppInfo) {
        String commcareVersion = showAppInfo ? AppUtils.getCurrentVersionString() : AppUtils.getCommCareVersionString();
        String customAcknowledgment = Localization.getWithDefault("custom.acknowledgement", "");
        String message = StringUtils.getStringRobust(context, R.string.about_dialog, new String[]{commcareVersion, customAcknowledgment});
        return MarkupUtil.returnMarkdown(context, message);
    }

    /**
     * Build dialog that tells user why they should authorize a given
     * permission. Pressing positive button launches the system's permission
     * request dialgo
     *
     * @param permRequester interface for launching system permission request
     *                      dialog
     */
    public static CommCareAlertDialog buildPermissionRequestDialog(AppCompatActivity activity,
                                                           final RuntimePermissionRequester permRequester,
                                                           final int requestCode,
                                                           String title,
                                                           String body) {
        View view = LayoutInflater.from(activity).inflate(R.layout.scrolling_info_dialog, null);
        TextView bodyText = view.findViewById(R.id.dialog_text);
        bodyText.setText(body);
        TextView titleText = view.findViewById(R.id.dialog_title_text);
        titleText.setText(title);

        CustomViewAlertDialog dialog = new CustomViewAlertDialog(view);
        dialog.setPositiveButton(Localization.get("dialog.ok"), (dialog1, which) -> {
            permRequester.requestNeededPermissions(requestCode);
            dialog1.dismiss();
        });

        return dialog;
    }
}

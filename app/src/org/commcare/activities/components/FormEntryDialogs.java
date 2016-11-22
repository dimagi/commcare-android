package org.commcare.activities.components;

import android.view.View;

import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;

public class FormEntryDialogs {
    /**
     * Create a dialog with options to save and exit, save, or quit without saving
     */
    public static void createQuitDialog(final FormEntryActivity activity, boolean isIncompleteEnabled) {
        final PaneledChoiceDialog dialog = new PaneledChoiceDialog(activity,
                StringUtils.getStringRobust(activity, R.string.quit_form_title));

        View.OnClickListener stayInFormListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportFormExit(GoogleAnalyticsFields.LABEL_BACK_TO_FORM);
                activity.dismissAlertDialog();
            }
        };
        DialogChoiceItem stayInFormItem = new DialogChoiceItem(
                StringUtils.getStringRobust(activity, R.string.do_not_exit),
                R.drawable.ic_blue_forward,
                stayInFormListener);

        View.OnClickListener exitFormListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleAnalyticsUtils.reportFormExit(GoogleAnalyticsFields.LABEL_EXIT_NO_SAVE);
                activity.discardChangesAndExit();
                activity.dismissAlertDialog();
            }
        };
        DialogChoiceItem quitFormItem = new DialogChoiceItem(
                StringUtils.getStringRobust(activity, R.string.do_not_save),
                R.drawable.icon_exit_form,
                exitFormListener);

        DialogChoiceItem[] items;
        if (isIncompleteEnabled) {
            View.OnClickListener saveIncompleteListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoogleAnalyticsUtils.reportFormExit(GoogleAnalyticsFields.LABEL_SAVE_AND_EXIT);
                    activity.saveFormToDisk(FormEntryConstants.EXIT);
                    activity.dismissAlertDialog();
                }
            };
            DialogChoiceItem saveIncompleteItem = new DialogChoiceItem(
                    StringUtils.getStringRobust(activity, R.string.keep_changes),
                    R.drawable.ic_incomplete_orange,
                    saveIncompleteListener);
            items = new DialogChoiceItem[]{stayInFormItem, quitFormItem, saveIncompleteItem};
        } else {
            items = new DialogChoiceItem[]{stayInFormItem, quitFormItem};
        }
        dialog.setChoiceItems(items);
        activity.showAlertDialog(dialog);
    }

    /**
     * Creates and displays a dialog allowing the user to set the language for the form.
     */
    public static void createLanguageDialog(final FormEntryActivity activity) {
        final PaneledChoiceDialog dialog = new PaneledChoiceDialog(activity,
                StringUtils.getStringRobust(activity, R.string.choose_language));

        final String[] languages = FormEntryActivity.mFormController.getLanguages();
        DialogChoiceItem[] choiceItems = new DialogChoiceItem[languages.length];
        for (int i = 0; i < languages.length; i++) {
            final int index = i;
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activity.setFormLanguage(languages, index);
                }
            };
            choiceItems[i] = new DialogChoiceItem(languages[i], -1, listener);
        }

        dialog.addButton(StringUtils.getStringSpannableRobust(activity, R.string.cancel).toString(),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        activity.dismissAlertDialog();
                    }
                }
        );

        dialog.setChoiceItems(choiceItems);
        activity.showAlertDialog(dialog);
    }
}

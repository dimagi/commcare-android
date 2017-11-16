package org.commcare.activities.components;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.view.View;

import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.utils.ChangeLocaleUtil;
import org.commcare.utils.GeoUtils;
import org.commcare.utils.StringUtils;
import org.commcare.views.ViewUtil;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.commcare.views.widgets.QuestionWidget;
import org.javarosa.core.services.locale.Localization;

import java.util.Set;

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
                activity.dismissAlertDialog();
                ViewUtil.hideVirtualKeyboard(activity);
                activity.discardChangesAndExit();
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
                Localization.get("home.menu.locale.select"));

        final String[] languageCodes = FormEntryActivity.mFormController.getLanguages();
        final String[] localizedLanguages = ChangeLocaleUtil.translateLocales(languageCodes);

        DialogChoiceItem[] choiceItems = new DialogChoiceItem[languageCodes.length];
        for (int i = 0; i < languageCodes.length; i++) {
            final int index = i;
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activity.setFormLanguage(languageCodes, index);
                }
            };
            choiceItems[i] = new DialogChoiceItem(localizedLanguages[i], -1, listener);
        }

        dialog.setChoiceItems(choiceItems);
        activity.showAlertDialog(dialog);
    }

    /**
     * Confirm clear answer dialog
     */
    public static void createClearDialog(final FormEntryActivity activity,
                                         final QuestionWidget qw) {
        String title = StringUtils.getStringRobust(activity, R.string.clear_answer_ask);
        String question = qw.getPrompt().getLongText();
        if (question == null) {
            question = "";
        } else if (question.length() > 50) {
            question = question.substring(0, 50) + "...";
        }
        String msg = StringUtils.getStringSpannableRobust(activity, R.string.clearanswer_confirm, question).toString();
        StandardAlertDialog d = new StandardAlertDialog(activity, title, msg);
        d.setIcon(android.R.drawable.ic_dialog_info);

        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE:
                        activity.clearAnswer(qw);
                        activity.saveAnswersForCurrentScreen(FormEntryConstants.DO_NOT_EVALUATE_CONSTRAINTS);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
                activity.dismissAlertDialog();
            }
        };
        d.setPositiveButton(StringUtils.getStringSpannableRobust(activity, R.string.discard_answer), quitListener);
        d.setNegativeButton(StringUtils.getStringSpannableRobust(activity, R.string.clear_answer_no), quitListener);
        activity.showAlertDialog(d);
    }

    public static void handleNoGpsBroadcast(final FormEntryActivity activity) {
        LocationManager manager = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
        Set<String> providers = GeoUtils.evaluateProviders(manager);
        if (providers.isEmpty()) {
            DialogInterface.OnClickListener onChangeListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    if (i == DialogInterface.BUTTON_POSITIVE) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        activity.startActivity(intent);
                    }
                    activity.dismissAlertDialog();
                }
            };
            GeoUtils.showNoGpsDialog(activity, onChangeListener);
        }
    }
}

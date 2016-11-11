package org.commcare.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.utils.ChangeLocaleUtil;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 11/11/16.
 */

public abstract class HomeActionsCapableCommCareActivity<T> extends SyncCapableCommCareActivity<T> {

    public static final int GET_INCOMPLETE_FORM = 16;
    protected static final int PREFERENCES_ACTIVITY = 512;
    protected static final int ADVANCED_ACTIONS_ACTIVITY = 1024;

    private int mDeveloperModeClicks = 0;

    public void launchUpdateActivity() {
        Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
        startActivity(i);
    }

    protected void goToFormArchive(boolean incomplete) {
        goToFormArchive(incomplete, null);
    }

    protected void goToFormArchive(boolean incomplete, FormRecord record) {
        if (incomplete) {
            GoogleAnalyticsUtils.reportViewArchivedFormsList(GoogleAnalyticsFields.LABEL_INCOMPLETE);
        } else {
            GoogleAnalyticsUtils.reportViewArchivedFormsList(GoogleAnalyticsFields.LABEL_COMPLETE);
        }
        Intent i = new Intent(getApplicationContext(), FormRecordListActivity.class);
        if (incomplete) {
            i.putExtra(FormRecord.META_STATUS, FormRecord.STATUS_INCOMPLETE);
        }
        if (record != null) {
            i.putExtra(FormRecordListActivity.KEY_INITIAL_RECORD_ID, record.getID());
        }
        startActivityForResult(i, GET_INCOMPLETE_FORM);
    }

    protected void showAboutCommCareDialog() {
        CommCareAlertDialog dialog = DialogCreationHelpers.buildAboutCommCareDialog(this);
        dialog.makeCancelable();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                handleDeveloperModeClicks();
            }
        });
        showAlertDialog(dialog);
    }

    private void handleDeveloperModeClicks() {
        mDeveloperModeClicks++;
        if (mDeveloperModeClicks == 4) {
            CommCareApplication.instance().getCurrentApp().getAppPreferences()
                    .edit()
                    .putString(DeveloperPreferences.SUPERUSER_ENABLED, CommCarePreferences.YES)
                    .commit();
            Toast.makeText(this, Localization.get("home.developer.options.enabled"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    protected void showLocaleChangeMenu(final CommCareActivityUIController uiController) {
        final PaneledChoiceDialog dialog =
                new PaneledChoiceDialog(this, Localization.get("home.menu.locale.select"));

        AdapterView.OnItemClickListener listClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String[] localeCodes = ChangeLocaleUtil.getLocaleCodes();
                if (position >= localeCodes.length) {
                    Localization.setLocale("default");
                } else {
                    Localization.setLocale(localeCodes[position]);
                }
                // rebuild home buttons in case language changed;
                if (uiController != null) {
                    uiController.setupUI();
                }
                rebuildOptionsMenu();
                dismissAlertDialog();
            }
        };

        dialog.setChoiceItems(buildLocaleChoices(), listClickListener);
        showAlertDialog(dialog);
    }

    private static DialogChoiceItem[] buildLocaleChoices() {
        String[] locales = ChangeLocaleUtil.getLocaleNames();
        DialogChoiceItem[] choices = new DialogChoiceItem[locales.length];
        for (int i = 0; i < choices.length; i++) {
            choices[i] = DialogChoiceItem.nonListenerItem(locales[i]);
        }
        return choices;
    }

    protected void startAdvancedActionsActivity() {
        startActivityForResult(new Intent(this, AdvancedActionsActivity.class),
                ADVANCED_ACTIONS_ACTIVITY);
    }

    public static void createPreferencesMenu(Activity activity) {
        Intent i = new Intent(activity, CommCarePreferences.class);
        activity.startActivityForResult(i, PREFERENCES_ACTIVITY);
    }

}

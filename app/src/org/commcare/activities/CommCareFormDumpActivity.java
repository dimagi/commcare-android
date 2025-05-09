package org.commcare.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.AdvancedActionsPreferences;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.tasks.DumpTask;
import org.commcare.tasks.SendTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.commcare.utils.StorageUtils;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.NotificationMessageFactory.StockMessages;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.util.Vector;

/**
 * @author wspride
 */

@ManagedUi(R.layout.screen_form_dump)
public class CommCareFormDumpActivity extends SessionAwareCommCareActivity<CommCareFormDumpActivity> {
    private static final String TAG = CommCareFormDumpActivity.class.getSimpleName();
    public static final String DUMP_FORMS_ERROR = "DUMP_FORMS_ERROR";

    @UiElement(value = R.id.screen_bulk_form_prompt, locale = "bulk.form.prompt")
    TextView txtDisplayPrompt;

    @UiElement(value = R.id.screen_bulk_form_dump, locale = "bulk.form.dump")
    Button btnDumpForms;

    @UiElement(value = R.id.screen_bulk_form_submit, locale = "bulk.form.submit")
    Button btnSubmitForms;

    @UiElement(value = R.id.screen_bulk_form_messages, locale = "bulk.form.messages")
    TextView txtInteractiveMessages;

    public static final String AIRPLANE_MODE_CATEGORY = "airplane-mode";

    private static boolean acknowledgedRisk = false;

    public static final String EXTRA_FILE_DESTINATION = "ccodk_mia_filedest";

    private int formsOnPhone;
    private int formsOnSD;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        final String url = this.getString(R.string.PostURL);

        updateCounters();

        btnSubmitForms.setOnClickListener(v -> {

            formsOnSD = getDumpFiles().length;
            Logger.log(LogTypes.TYPE_FORM_DUMP, "Send task found " + formsOnSD + " forms on the phone.");

            //if there're no forms to dump, just return
            if (formsOnSD == 0) {
                txtInteractiveMessages.setText(localize("bulk.form.no.unsynced.submit"));
                transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                return;
            }

            SharedPreferences settings = CommCareApplication.instance().getCurrentApp().getAppPreferences();
            SendTask<CommCareFormDumpActivity> mSendTask = new SendTask<CommCareFormDumpActivity>(
                    settings.getString(ServerUrls.PREFS_SUBMISSION_URL_KEY, url),
                    getFolderPath()) {
                @Override
                protected void deliverResult(CommCareFormDumpActivity receiver, Boolean result) {
                    if (result == Boolean.TRUE) {
                        CommCareApplication.notificationManager().clearNotifications(AIRPLANE_MODE_CATEGORY);
                        Intent i = new Intent(getIntent());
                        i.putExtra(AdvancedActionsPreferences.KEY_NUMBER_DUMPED, formsOnSD);
                        receiver.setResult(BULK_SEND_ID, i);
                        Logger.log(LogTypes.TYPE_FORM_DUMP, "Successfully submitted " + formsOnSD + " forms.");
                        receiver.finish();
                    } else {
                        //assume that we've already set the error message, but make it look scary
                        CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Sync_AirplaneMode, AIRPLANE_MODE_CATEGORY));
                        receiver.updateCounters();
                        receiver.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                    }
                }

                @Override
                protected void deliverUpdate(CommCareFormDumpActivity receiver, String... update) {
                    receiver.updateProgress(update[0], BULK_SEND_ID);
                    receiver.txtInteractiveMessages.setText(update[0]);
                }

                @Override
                protected void deliverError(CommCareFormDumpActivity receiver, Exception e) {
                    Logger.log(LogTypes.TYPE_FORM_DUMP, "Send failed with exception: " + e.getMessage());
                    receiver.txtInteractiveMessages.setText(Localization.get("bulk.form.error", new String[]{e.getMessage()}));
                    receiver.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                }
            };
            mSendTask.connect(CommCareFormDumpActivity.this);
            mSendTask.execute();
        });

        btnDumpForms.setOnClickListener(v -> {

            Logger.log(LogTypes.TYPE_FORM_DUMP, "Dump task found " + formsOnSD + " forms on the SD card.");

            if (formsOnPhone == 0) {
                txtInteractiveMessages.setText(Localization.get("bulk.form.no.unsynced.dump"));
                transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                return;
            }
            DumpTask mDumpTask = new DumpTask(getApplicationContext()) {
                @Override
                protected void deliverResult(CommCareFormDumpActivity receiver, Boolean result) {
                    if (result == Boolean.TRUE) {
                        Intent i = new Intent(getIntent());
                        i.putExtra(AdvancedActionsPreferences.KEY_NUMBER_DUMPED, formsOnPhone);
                        receiver.setResult(BULK_DUMP_ID, i);
                        Logger.log(LogTypes.TYPE_FORM_DUMP, "Successfully dumped " + formsOnPhone + " forms.");
                        receiver.finish();
                    } else {
                        //assume that we've already set the error message, but make it look scary
                        receiver.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                    }
                }

                @Override
                protected void deliverUpdate(CommCareFormDumpActivity receiver, String... update) {
                    receiver.updateProgress(update[0], BULK_DUMP_ID);
                    receiver.txtInteractiveMessages.setText(update[0]);
                }

                @Override
                protected void deliverError(CommCareFormDumpActivity receiver, Exception e) {
                    Logger.log(LogTypes.TYPE_FORM_DUMP, "Dump failed with exception: " + e.getMessage());
                    receiver.txtInteractiveMessages.setText(Localization.get("bulk.form.error", new String[]{e.getMessage()}));
                    receiver.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                }
            };
            mDumpTask.connect(CommCareFormDumpActivity.this);
            mDumpTask.execute();
        });

        if (!acknowledgedRisk) {
            showWarningMessage();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(DUMP_FORMS_ERROR, txtInteractiveMessages.getText().toString());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        txtInteractiveMessages.setText(savedInstanceState.getString(DUMP_FORMS_ERROR));
        transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
    }

    private void showWarningMessage() {
        StandardAlertDialog d = new StandardAlertDialog(
                Localization.get("bulk.form.alert.title"), Localization.get("bulk.form.warning"));
        DialogInterface.OnClickListener listener = (dialog, id) -> {
            dialog.dismiss();
            if (id == AlertDialog.BUTTON_POSITIVE) {
                acknowledgedRisk = true;
                dialog.dismiss();
            } else {
                exitDump();
            }
        };
        d.setPositiveButton("OK", listener);
        d.setNegativeButton("NO", listener);
        showAlertDialog(d);
    }

    private void updateCounters() {
        Vector<Integer> ids = getUnsyncedForms();
        File[] files = getDumpFiles();
        formsOnPhone = ids.size();
        formsOnSD = files.length;
        setDisplayText();
    }

    private void setDisplayText() {
        btnDumpForms.setText(this.localize("bulk.form.dump.2", new String[]{"" + formsOnPhone}));
        btnSubmitForms.setText(this.localize("bulk.form.submit.2", new String[]{"" + formsOnSD}));
        txtDisplayPrompt.setText(this.localize("bulk.form.prompt", new String[]{"" + formsOnPhone, "" + formsOnSD}));
    }

    private String getFolderName() {
        SharedPreferences settings = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return settings.getString(HiddenPreferences.DUMP_FOLDER_PATH, Localization.get("bulk.form.foldername"));
    }

    private File getFolderPath() {
        String fileRoot = FileUtil.getDumpDirectory(this);
        if (fileRoot == null) {
            return null;
        }
        String folderName = getFolderName();
        File dumpDirectory = new File(fileRoot + "/" + folderName);
        Logger.log(LogTypes.TYPE_FORM_DUMP, "Got folder path " + dumpDirectory);
        return dumpDirectory;
    }

    private File[] getDumpFiles() {
        File dumpDirectory = getFolderPath();
        if (dumpDirectory == null || !dumpDirectory.isDirectory()) {
            return new File[]{};
        }
        File[] files = dumpDirectory.listFiles();
        Logger.log(LogTypes.TYPE_FORM_DUMP, "Found " + files.length + " dump files.");
        return files;
    }

    private Vector<Integer> getUnsyncedForms() {
        SqlStorage<FormRecord> storage = CommCareApplication.instance().getUserStorage(FormRecord.class);
        Vector<Integer> ids = StorageUtils.getUnsentOrUnprocessedFormIdsForCurrentApp(storage);
        Logger.log(LogTypes.TYPE_FORM_DUMP, "Found " + ids.size() + " unsynced forms.");
        return ids;
    }

    private void exitDump() {
        finish();
    }

    @Override
    public void taskCancelled() {
        txtInteractiveMessages.setText(Localization.get("bulk.form.cancel"));
        this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
    }
    
    /* Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        if (taskId == DumpTask.BULK_DUMP_ID) {
            title = Localization.get("bulk.dump.dialog.title");
            message = Localization.get("bulk.dump.dialog.progress", new String[]{"0"});
        } else if (taskId == SendTask.BULK_SEND_ID) {
            title = Localization.get("bulk.send.dialog.title");
            message = Localization.get("bulk.send.dialog.progress", new String[]{"0"});
        } else {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareFormDumpActivity");
            return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }
}

package org.commcare.activities.components;

import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.models.ODKStorage;
import org.commcare.models.database.SqlStorage;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Vector;

import javax.annotation.Nullable;

/**
 * Tracks the current form instance's xml file and auxilary files (multimedia)
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class FormEntryInstanceState {
    public static final String KEY_FORM_RECORD_DESTINATION = "instancedestination";
    // Identifies the gp of the form used to launch form entry
    private static final String KEY_FORMPATH = "formpath";
    // Path to a particular form instance

    @Nullable
    public static String mFormRecordPath;
    private String mFormRecordDestination;
    private String mFormDefPath;
    private final SqlStorage<FormRecord> formRecordStorage;

    public FormEntryInstanceState(SqlStorage<FormRecord> formRecordStorage) {
        this.formRecordStorage = formRecordStorage;
    }

    /**
     * Checks the database to determine if the current instance being edited has already been
     * 'marked completed'. A form can be 'unmarked' complete and then resaved.
     *
     * @return true if form has been marked completed, false otherwise.
     */
    public boolean isFormRecordComplete() {
        return FormRecord.isComplete(formRecordStorage, mFormRecordPath);
    }

    public Pair<Integer, Boolean> getFormDefIdForRecord(SqlStorage<FormDefRecord> formDefRecordStorage, int formRecordId, FormEntryInstanceState instanceState)
            throws FormEntryActivity.FormQueryException {
        FormRecord formRecord = FormRecord.getFormRecord(formRecordStorage, formRecordId);
        mFormRecordPath = formRecord.getFilePath();

        String formStatus = formRecord.getStatus();
        boolean isInstanceReadOnly =
                !FormRecord.STATUS_UNSTARTED.equals(formStatus) &&
                        !FormRecord.STATUS_INCOMPLETE.equals(formStatus);

        int appVersion = CommCareApplication.instance().getCurrentApp().getAppRecord().getVersionNumber();
        int formDefId = FormDefRecord.getLatestFormDefId(appVersion, formDefRecordStorage, formRecord.getXmlns());
        if (formDefId == -1) {
            String error = "No XForm definition defined for this form with namespace " + formRecord.getXmlns();
            Logger.log(LogTypes.SOFT_ASSERT, error);
            throw new FormEntryActivity.FormQueryException(error);
        } else {
            FormDefRecord formDefRecord = FormDefRecord.getFormDef(formDefRecordStorage, formDefId);
            instanceState.setFormDefPath(formDefRecord.getFilePath());
            return new Pair<>(formDefRecord.getID(), isInstanceReadOnly);
        }
    }

    /**
     * Get the default title for ODK's "Form title" field
     */
    public String getDefaultFormTitle(int formRecordId) {
        String saveName = FormEntryActivity.mFormController.getFormTitle();
        if (formRecordId != -1) {
            saveName = FormRecord.getFormRecord(formRecordStorage, formRecordId).getDisplayName();
        }
        return saveName;
    }

    public void saveState(Bundle outState) {
        outState.putString(KEY_FORM_RECORD_DESTINATION, mFormRecordDestination);
        outState.putString(KEY_FORMPATH, mFormDefPath);
    }

    public void loadState(Bundle savedInstanceState) {
        if (savedInstanceState.containsKey(KEY_FORM_RECORD_DESTINATION)) {
            mFormRecordDestination = savedInstanceState.getString(KEY_FORM_RECORD_DESTINATION);
        }
        if (savedInstanceState.containsKey(KEY_FORMPATH)) {
            mFormDefPath = savedInstanceState.getString(KEY_FORMPATH);
        }
    }

    public void loadFromIntent(Intent intent) {
        if (intent.hasExtra(KEY_FORM_RECORD_DESTINATION)) {
            this.mFormRecordDestination = intent.getStringExtra(KEY_FORM_RECORD_DESTINATION);
        } else {
            mFormRecordDestination = ODKStorage.FORM_RECORD_PATH;
        }
    }

    public void initFormRecordPath() {
        // Create new answer folder.
        String time =
                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                        .format(Calendar.getInstance().getTime());
        String file =
                mFormDefPath.substring(mFormDefPath.lastIndexOf(File.separator) + 1, mFormDefPath.lastIndexOf('.'));
        String path = mFormRecordDestination + file + "_" + time;
        if (FileUtil.createFolder(path)) {
            mFormRecordPath = path + File.separator + file + "_" + time + ".xml";
        }
    }

    public void setFormDefPath(String formDefPath) {
        mFormDefPath = formDefPath;
    }

    public static String getInstanceFolder() {
        return mFormRecordPath.substring(0, mFormRecordPath.lastIndexOf(File.separator) + 1);
    }

    public static void setFormRecordPath(@Nullable String formRecordPath) {
        mFormRecordPath = formRecordPath;
    }
}

package org.commcare.activities.components;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Vector;

import org.commcare.activities.FormEntryActivity;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.app.models.InstanceRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.models.ODKStorage;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.provider.InstanceProviderAPI;
import org.commcare.utils.FileUtil;

/**
 * Tracks the current form instance's xml file and auxilary files (multimedia)
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class FormEntryInstanceState {
    public static final String KEY_INSTANCEDESTINATION = "instancedestination";
    // Identifies the gp of the form used to launch form entry
    private static final String KEY_FORMPATH = "formpath";
    // Path to a particular form instance
    public static String mInstancePath;
    private String mInstanceDestination;
    private String mFormPath;

    /**
     * Checks the database to determine if the current instance being edited has already been
     * 'marked completed'. A form can be 'unmarked' complete and then resaved.
     *
     * @return true if form has been marked completed, false otherwise.
     */
    public static boolean isInstanceComplete() {
        return InstanceRecord.isInstanceComplete(mInstancePath);
    }

    public static Pair<Integer, Boolean> getFormDefIdForInstance(int instanceId, FormEntryInstanceState instanceState)
            throws FormEntryActivity.FormQueryException {
        Boolean isInstanceReadOnly = false;
        InstanceRecord instanceRecord = InstanceRecord.getInstance(instanceId);
        mInstancePath = instanceRecord.getFilePath();

        //If this form is both already completed
        if (InstanceProviderAPI.STATUS_COMPLETE.equals(instanceRecord.getStatus())) {
            if (!Boolean.parseBoolean(instanceRecord.getCanEditWhenComplete())) {
                isInstanceReadOnly = true;
            }
        }

        Vector<FormDefRecord> formDefRecords = FormDefRecord.getFormDefsByJrFormId(instanceRecord.getJrFormId());

        if (formDefRecords.size() == 1) {
            FormDefRecord formDefRecord = formDefRecords.get(0);
            instanceState.setFormPath(formDefRecord.getFilePath());
            return new Pair<>(formDefRecord.getID(), isInstanceReadOnly);
        } else if (formDefRecords.size() < 1) {
            throw new FormEntryActivity.FormQueryException("Parent form does not exist");
        } else {
            throw new FormEntryActivity.FormQueryException("More than one possible parent form");
        }
    }

    /**
     * Get the default title for ODK's "Form title" field
     */
    public static String getDefaultFormTitle(int instanceId) {
        String saveName = FormEntryActivity.mFormController.getFormTitle();
        if (instanceId != -1) {
            saveName = InstanceRecord.getInstance(instanceId).getDisplayName();
        }
        return saveName;
    }

    public void saveState(Bundle outState) {
        outState.putString(KEY_INSTANCEDESTINATION, mInstanceDestination);
        outState.putString(KEY_FORMPATH, mFormPath);
    }

    public void loadState(Bundle savedInstanceState) {
        if (savedInstanceState.containsKey(KEY_INSTANCEDESTINATION)) {
            mInstanceDestination = savedInstanceState.getString(KEY_INSTANCEDESTINATION);
        }
        if (savedInstanceState.containsKey(KEY_FORMPATH)) {
            mFormPath = savedInstanceState.getString(KEY_FORMPATH);
        }
    }

    public void loadFromIntent(Intent intent) {
        if (intent.hasExtra(KEY_INSTANCEDESTINATION)) {
            this.mInstanceDestination = intent.getStringExtra(KEY_INSTANCEDESTINATION);
        } else {
            mInstanceDestination = ODKStorage.INSTANCES_PATH;
        }
    }

    public String getFormPath() {
        return mFormPath;
    }

    public void initInstancePath() {
        // Create new answer folder.
        String time =
                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                        .format(Calendar.getInstance().getTime());
        String file =
                mFormPath.substring(mFormPath.lastIndexOf(File.separator) + 1, mFormPath.lastIndexOf('.'));
        String path = mInstanceDestination + file + "_" + time;
        if (FileUtil.createFolder(path)) {
            FormEntryInstanceState.mInstancePath = path + File.separator + file + "_" + time + ".xml";
        }
    }

    public void setFormPath(String path) {
        mFormPath = path;
    }

    public static String getInstanceFolder() {
        return mInstancePath.substring(0, mInstancePath.lastIndexOf(File.separator) + 1);
    }
}

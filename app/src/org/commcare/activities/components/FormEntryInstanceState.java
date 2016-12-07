package org.commcare.activities.components;

import android.content.Intent;
import android.os.Bundle;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.commcare.models.ODKStorage;
import org.commcare.utils.FileUtil;

/**
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
                mFormPath.substring(mFormPath.lastIndexOf('/') + 1, mFormPath.lastIndexOf('.'));
        String path = mInstanceDestination + file + "_" + time;
        if (FileUtil.createFolder(path)) {
            FormEntryInstanceState.mInstancePath = path + "/" + file + "_" + time + ".xml";
        }
    }

    public void setFormPath(String path) {
        mFormPath = path;
    }

    public static String getInstanceFolder() {
        return mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);
    }
}

package org.commcare.android.database.app.models;

import android.database.Cursor;
import android.database.SQLException;

import org.apache.commons.lang3.StringUtils;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.resources.model.Resource;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.javarosa.core.services.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

@Table(FormDefRecord.STORAGE_KEY)
public class FormDefRecord extends Persisted {

    public static final String STORAGE_KEY = "form_def";

    public static final String META_DISPLAY_NAME = "displayName";
    public static final String META_JR_FORM_ID = "jrFormId";
    public static final String META_FORM_FILE_PATH = "formFilePath";
    public static final String META_FORM_MEDIA_PATH = "formMediaPath";

    // these are null unless you enter something and aren't currently used
    public static final String META_MODEL_VERSION = "modelVersion";
    public static final String META_UI_VERSION = "uiVersion";
    public static final String META_RESOURCE_VERSION = "resourceVersion";

    @Persisting(1)
    @MetaField(META_DISPLAY_NAME)
    private String mDisplayName;

    @Persisting(2)
    @MetaField(META_JR_FORM_ID)
    private String mJrFormId;

    @Persisting(3)
    @MetaField(META_FORM_FILE_PATH)
    private String mFormFilePath;

    @Persisting(4)
    @MetaField(META_FORM_MEDIA_PATH)
    private String mFormMediaPath;

    @Persisting(value = 5, nullable = true)
    @MetaField(META_MODEL_VERSION)
    private int mModelVersion = -1;

    @Persisting(value = 6, nullable = true)
    @MetaField(META_UI_VERSION)
    private int mUiVersion = -1;

    // Corresponding resource version for this Form Record
    @Persisting(value = 7, nullable = true)
    @MetaField(META_RESOURCE_VERSION)
    private int mResourceVersion = -1;


    //    Serialization Only!
    public FormDefRecord() {
    }

    public FormDefRecord(String displayName, String jrFormId, String formFilePath, String formMediaPath, int resourceVersion) {
        checkFilePath(formFilePath);
        mDisplayName = displayName;
        mJrFormId = jrFormId;
        mFormFilePath = formFilePath;
        mFormMediaPath = formMediaPath;
        mResourceVersion = resourceVersion;
    }

    // For migration from FormDefRecordV12
    public FormDefRecord(FormDefRecordV12 oldFormDefRecord) {
        mDisplayName = oldFormDefRecord.getDisplayName();
        mJrFormId = oldFormDefRecord.getJrFormId();
        mFormFilePath = oldFormDefRecord.getFilePath();
        mFormMediaPath = oldFormDefRecord.getMediaPath();
        mModelVersion = oldFormDefRecord.getModelVersion();
        mUiVersion = oldFormDefRecord.getUiVersion();
    }

    public static Vector<Integer> getFormDefIdsByJrFormId(SqlStorage<FormDefRecord> formDefRecordStorage, String jrFormId) {
        return formDefRecordStorage.getIDsForValue(META_JR_FORM_ID, jrFormId);
    }

    public static Vector<FormDefRecord> getFormDefsByJrFormId(SqlStorage<FormDefRecord> formDefRecordStorage, String jrFormId) {
        return formDefRecordStorage.getRecordsForValue(META_JR_FORM_ID, jrFormId);
    }

    public int save(SqlStorage<FormDefRecord> formDefRecordStorage) {
        // if we don't have a path to the file, the rest are irrelevant.
        // it should fail anyway because you can't have a null file path.
        if (StringUtils.isEmpty(mFormFilePath)) {
            Logger.log(LogTypes.SOFT_ASSERT, "Empty value for mFormFilePath while saving FormDefRecord");
        }

        // Make sure that the necessary fields are all set
        File form = new File(mFormFilePath);
        if (StringUtils.isEmpty(mDisplayName)) {
            mDisplayName = form.getName();
        }

        if (StringUtils.isEmpty(mFormMediaPath)) {
            mFormMediaPath = getMediaPath(mFormFilePath);
        }

        formDefRecordStorage.write(this);

        if (recordId == -1) {
            throw new SQLException("Failed to save the FormDefRecord " + toString());
        }
        return recordId;
    }

    private static String getMediaPath(String formFilePath) {
        String pathNoExtension = formFilePath.substring(0, formFilePath.lastIndexOf("."));
        return pathNoExtension + "-media";
    }

    public static void updateFilePath(SqlStorage<FormDefRecord> formDefRecordStorage, int recordId, String formFilePath) {
        FormDefRecord existingRecord = formDefRecordStorage.read(recordId);
        existingRecord.updateFilePath(formDefRecordStorage, formFilePath);
    }


    private void updateFilePath(SqlStorage<FormDefRecord> formDefRecordStorage, String newFilePath) {
        checkFilePath(newFilePath);
        File newFormFile = new File(newFilePath);
        try {
            if (new File(mFormFilePath).getCanonicalPath().equals(newFormFile.getCanonicalPath())) {
                // Files are the same, so we may have just copied over something we had already
            } else {
                // New file name. This probably won't ever happen, though.
                FileUtil.deleteFileOrDir(mFormFilePath);
            }
        } catch (IOException ioe) {
            //we only get here if we couldn't canonicalize, in which case we can't risk deleting the old file
            //so don't do anything.
        }

        // Set new values now
        mFormFilePath = newFilePath;
        mFormMediaPath = getMediaPath(newFilePath);
        formDefRecordStorage.write(this);
    }

    private static void checkFilePath(String formFilePath) {
        if (StringUtils.isEmpty(formFilePath)) {
            throw new IllegalArgumentException("formFilePath can't by null or empty");
        }
    }


    /**
     *
     * @param minResourceVersion min resource version beyond which form records will be discarded
     * @param formDefStorage form storage
     * @param namespace namespace of the desired form
     * @return the formId of the formDefRecord with highest form def resource version for our namespace
     */
    public static int getLatestFormDefId(int minResourceVersion, SqlStorage<FormDefRecord> formDefStorage, String namespace) {
        Vector<Integer> formsForOurNamespace = FormDefRecord.getFormDefIdsByJrFormId(formDefStorage, namespace);
        int maxFormResourceVersion = -1;
        int latestFormId = -1;
        for (int i = 0; i < formsForOurNamespace.size(); i++) {
            FormDefRecord formDefRecordItem = formDefStorage.read(formsForOurNamespace.get(i));

            // All the update records are going to have a version greater than the installed version, exclude them
            if (formDefRecordItem.getResourceVersion() <= minResourceVersion) {
                if (formDefRecordItem.getResourceVersion() >= maxFormResourceVersion) {
                    maxFormResourceVersion = formDefRecordItem.getResourceVersion();
                    latestFormId = formDefRecordItem.getID();
                }
            }
        }
        return latestFormId;
    }

    public static FormDefRecord getFormDef(SqlStorage<FormDefRecord> formDefRecordStorage, int formId) {
        return formDefRecordStorage.read(formId);
    }

    public String getFilePath() {
        return mFormFilePath;
    }

    public String getMediaPath() {
        return mFormMediaPath;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public Integer getUiVersion() {
        return mUiVersion;
    }

    public int getResourceVersion() {
        return mResourceVersion;
    }
}

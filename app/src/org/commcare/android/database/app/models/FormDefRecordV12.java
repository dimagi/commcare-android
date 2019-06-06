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
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.javarosa.core.services.Logger;

import java.io.File;
import java.io.IOException;

import static org.commcare.android.database.app.models.FormDefRecord.META_DISPLAY_NAME;
import static org.commcare.android.database.app.models.FormDefRecord.META_FORM_FILE_PATH;
import static org.commcare.android.database.app.models.FormDefRecord.META_FORM_MEDIA_PATH;
import static org.commcare.android.database.app.models.FormDefRecord.META_JR_FORM_ID;
import static org.commcare.android.database.app.models.FormDefRecord.META_MODEL_VERSION;
import static org.commcare.android.database.app.models.FormDefRecord.META_UI_VERSION;

// Model for Form Def Record pre App DB v13
@Table(FormDefRecord.STORAGE_KEY)
public class FormDefRecordV12  extends Persisted {

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

    // Serialization Only!
    public FormDefRecordV12() {
    }

    // Only for DB Migration
    public FormDefRecordV12(Cursor cursor) {
        mDisplayName = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.DISPLAY_NAME));
        mJrFormId = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.JR_FORM_ID));
        mModelVersion = cursor.getInt(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.MODEL_VERSION));
        mUiVersion = cursor.getInt(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.UI_VERSION));
        mFormMediaPath = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH));
        mFormFilePath = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH));
    }

    public void updateFilePath(SqlStorage<FormDefRecordV12> formDefRecordStorage, String newFilePath) {
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


    public int save(SqlStorage<FormDefRecordV12> formDefRecordStorage) {
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

    public String getJrFormId() {
        return mJrFormId;
    }

    public int getModelVersion() {
        return mModelVersion;
    }
}

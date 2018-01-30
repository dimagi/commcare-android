package org.commcare.android.database.app.models;

import android.database.Cursor;
import android.database.SQLException;
import android.os.Environment;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.utils.FileUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

@Table(FormDefRecord.STORAGE_KEY)
public class FormDefRecord extends Persisted {

    public static final String STORAGE_KEY = "form_def";

    public static final String META_DISPLAY_NAME = "displayName";
    public static final String META_DESCRIPTION = "description";
    public static final String META_JR_FORM_ID = "jrFormId";
    public static final String META_FORM_FILE_PATH = "formFilePath";
    public static final String META_SUBMISSION_URI = "submissionUri";
    public static final String META_BASE64_RSA_PUBLIC_KEY = "base64RsaPublicKey";

    // these are generated for you (but you can insert something else if you want)
    public static final String META_DISPLAY_SUBTEXT = "displaySubtext";
    public static final String META_MD5_HASH = "md5Hash";
    public static final String META_DATE = "date";
    public static final String META_JRCACHE_FILE_PATH = "jrcacheFilePath";
    public static final String META_FORM_MEDIA_PATH = "formMediaPath";

    // these are null unless you enter something and aren't currently used
    public static final String META_MODEL_VERSION = "modelVersion";
    public static final String META_UI_VERSION = "uiVersion";

    // this is null on create, and can only be set on an update.
    public static final String META_LANGUAGE = "language";
    private static SqlStorage<FormDefRecord> sFormDefStorage;

    @Persisting(1)
    @MetaField(META_DISPLAY_NAME)
    private String mDisplayName;

    @Persisting(value = 2, nullable = true)
    @MetaField(META_DESCRIPTION)
    private String mDescription;

    @Persisting(3)
    @MetaField(META_JR_FORM_ID)
    private String mJrFormId;

    @Persisting(4)
    @MetaField(META_FORM_FILE_PATH)
    private String mFormFilePath;

    @Persisting(value = 5, nullable = true)
    @MetaField(META_SUBMISSION_URI)
    private String mSubmissionUri;

    @Persisting(value = 6, nullable = true)
    @MetaField(META_BASE64_RSA_PUBLIC_KEY)
    private String mBase64RsaPublicKey;

    @Persisting(7)
    @MetaField(META_DISPLAY_SUBTEXT)
    private String mDisplaySubtext;

    @Persisting(8)
    @MetaField(META_MD5_HASH)
    private String mMd5Hash;

    @Persisting(9)
    @MetaField(META_DATE)
    private Date mDate;

    @Persisting(10)
    @MetaField(META_JRCACHE_FILE_PATH)
    private String mJrcacheFilePath;

    @Persisting(11)
    @MetaField(META_FORM_MEDIA_PATH)
    private String mFormMediaPath;

    @Persisting(value = 12, nullable = true)
    @MetaField(META_MODEL_VERSION)
    private int mModelVersion = -1;

    @Persisting(value = 13, nullable = true)
    @MetaField(META_UI_VERSION)
    private int mUiVersion = -1;

    @Persisting(value = 14, nullable = true)
    @MetaField(META_LANGUAGE)
    private String mLanguage;

    //    Serialization Only!
    public FormDefRecord() {
    }

    public FormDefRecord(String displayName, String description, String jrFormId, String formFilePath, String formMediaPath) {
        mDisplayName = displayName;
        mDescription = description;
        mJrFormId = jrFormId;
        mFormFilePath = formFilePath;
        mFormMediaPath = formMediaPath;
    }

    // Only for DB Migration
    public FormDefRecord(Cursor cursor) {
        mDisplayName = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.DISPLAY_NAME));
        mDisplaySubtext = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.DISPLAY_SUBTEXT));
        mDescription = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.DESCRIPTION));
        mJrFormId = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.JR_FORM_ID));
        mModelVersion = cursor.getInt(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.MODEL_VERSION));
        mUiVersion = cursor.getInt(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.UI_VERSION));
        mMd5Hash = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.MD5_HASH));
        mFormMediaPath = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH));
        mFormFilePath = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH));
        mLanguage = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.LANGUAGE));
        mSubmissionUri = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.SUBMISSION_URI));
        mBase64RsaPublicKey = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.BASE64_RSA_PUBLIC_KEY));
        mJrcacheFilePath = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.JRCACHE_FILE_PATH));
        mDate = new Date(cursor.getInt(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.DATE)));
    }

    public static Vector<Integer> getFormDefIdsByJrFormId(String jrFormId) {
        return getFormDefStorage().getIDsForValue(META_JR_FORM_ID, jrFormId);
    }

    public static Vector<FormDefRecord> getFormDefsByJrFormId(String jrFormId) {
        return getFormDefStorage().getRecordsForValue(META_JR_FORM_ID, jrFormId);
    }

    public static SqlStorage<FormDefRecord> getFormDefStorage() {
        if (sFormDefStorage == null) {
            sFormDefStorage = CommCareApplication.instance().getAppStorage(FormDefRecord.class);
        }
        return sFormDefStorage;
    }

    public int save() {
        // if we don't have a path to the file, the rest are irrelevant.
        // it should fail anyway because you can't have a null file path.
        if (StringUtils.isEmpty(mFormFilePath)) {
            throw new SQLException("Can't save a form def with an empty form file path");
        }
        // Make sure that the necessary fields are all set
        if (mDate == null) {
            mDate = new Date();
        }

        if (mDisplaySubtext == null) {
            mDisplaySubtext = getDisplaySubtext();
        }

        File form = new File(mFormFilePath);
        if (StringUtils.isEmpty(mDisplayName)) {
            mDisplayName = form.getName();
        }
        mMd5Hash = FileUtil.getMd5Hash(form);

        if (StringUtils.isEmpty(mJrcacheFilePath)) {
            mJrcacheFilePath = getCachePath(mMd5Hash);
        }

        if (StringUtils.isEmpty(mFormMediaPath)) {
            mFormMediaPath = getMediaPath(mFormFilePath);
        }

        getFormDefStorage().write(this);

        if (recordId == -1) {
            throw new SQLException("Failed to save the FormDefRecord " + toString());
        }
        return recordId;
    }

    private String getMediaPath(String formFilePath) {
        String pathNoExtension = formFilePath.substring(0, formFilePath.lastIndexOf("."));
        return pathNoExtension + "-media";
    }

    private static String getCachePath(String md5Hash) {
        return Environment.getExternalStorageDirectory().getPath() + "odk/.cache" + md5Hash + ".formdef";
    }

    private static String getDisplaySubtext() {
        Date today = new Date();
        String ts = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(today);
        return "Added on " + ts;
    }

    public static void updateFilePath(int recordId, String formFilePath) {
        SqlStorage<FormDefRecord> formDefStorage = getFormDefStorage();
        FormDefRecord existingRecord = formDefStorage.read(recordId);
        existingRecord.updateFilePath(formFilePath);
    }


    public void updateFilePath(String newFilePath) {
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

        // we're updating our file, so update the md5
        // and get rid of the cache (doesn't harm anything)
        FileUtil.deleteFileOrDir(mJrcacheFilePath);
        String newMd5 = FileUtil.getMd5Hash(newFormFile);

        // Set new values now
        mFormMediaPath = getMediaPath(newFilePath);
        mMd5Hash = newMd5;
        mJrcacheFilePath = getCachePath(newMd5);

        getFormDefStorage().write(this);
    }

    public static FormDefRecord getFormDef(int formId) {
        return getFormDefStorage().read(formId);
    }

    public static int updateLanguage(String formPath, String language) {
        SqlStorage<FormDefRecord> formDefStorage = getFormDefStorage();
        Vector<FormDefRecord> formDefRecords = formDefStorage.getRecordsForValue(META_FORM_FILE_PATH, formPath);
        for (FormDefRecord formDefRecord : formDefRecords) {
            formDefRecord.mLanguage = language;
            formDefStorage.write(formDefRecord);
        }
        return formDefRecords.size();
    }

    public String getFilePath() {
        return mFormFilePath;
    }

    public String getMediaPath() {
        return mFormMediaPath;
    }

    public String getJrFormId() {
        return mJrFormId;
    }

    public String getSubmissionUri() {
        return mSubmissionUri;
    }

    public String getDisplayname() {
        return mDisplayName;
    }

    public Integer getModelVersion() {
        return mModelVersion;
    }

    public Integer getUiVersion() {
        return mUiVersion;
    }

    public String getBase64RsaPublicKey() {
        return mBase64RsaPublicKey;
    }

    public static void setFormDefStorage(SqlStorage<FormDefRecord> formDefStorage) {
        sFormDefStorage = formDefStorage;
    }
}

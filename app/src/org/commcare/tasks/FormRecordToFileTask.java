package org.commcare.tasks;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareWiFiDirectActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.dalvik.R;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.FileUtil;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.StorageUtils;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.ProcessIssues;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class transfers all of the FormRecords from form record storage and into our file system,
 * decrypting files as necessary. This task should really be merged with DumpTask.
 *
 * @author wspride
 */
public abstract class FormRecordToFileTask extends CommCareTask<String, String, Pair<Long, FormRecord[]>, CommCareWiFiDirectActivity> {
    private static final String TAG = AndroidLogger.TYPE_FORM_DUMP;

    private final Context c;
    // this is where the forms that have been pulled from FormRecord storage to the file system live
    private final File storedFormDirectory;

    public static final int PULL_TASK_ID = 721356;

    private static final String[] SUPPORTED_FILE_EXTS = {".xml", ".jpg", ".3gpp", ".3gp"};

    public FormRecordToFileTask(Context c, String formStoragePath) {
        this.c = c;
        this.storedFormDirectory = new File(formStoragePath);
        taskId = PULL_TASK_ID;
    }

    /**
     * Turn a FormRecord folder from storage into a standard file representation in our file system.
     * Return an int status code from FormUploadUtil corresponding to the outcome of the transfer
     */
    private long copyFileInstanceFromStorage(File formRecordFolder, SecretKeySpec decryptionKey) {
        File[] files = formRecordFolder.listFiles();
        Logger.log(TAG, "Trying to get instance with: " + files.length + " files.");

        File myDir = new File(storedFormDirectory, formRecordFolder.getName());
        myDir.mkdirs();

        logTransferBytes(files);

        final Cipher decryptCipher = FormUploadUtil.getDecryptCipher(decryptionKey);
        try {
            decryptCopyFiles(files, myDir, decryptCipher);
        } catch (IOException e){
            Log.d(TAG, "Copying file failed with: " + e.getMessage());
            publishProgress(("File writing failed: " + e.getMessage()));
            return FormUploadUtil.FAILURE;
        }

        // write any form.properties we want
        writeProperties(myDir);
        return FormUploadUtil.FULL_SUCCESS;
    }

    private void decryptCopyFiles(File[] files, File targetDirectory, Cipher decryptCipher) throws IOException{
        for (File file : files) {
            // This is not the ideal long term solution for determining whether we need decryption, but works
            if (file.getName().endsWith(".xml")) {
                FileUtil.copyFile(file, new File(targetDirectory, file.getName()), decryptCipher, null);
            } else {
                FileUtil.copyFile(file, new File(targetDirectory, file.getName()));
            }
        }
    }
    private static boolean isSupportedFiletype(File file){
        for (String ext : SUPPORTED_FILE_EXTS) {
            if (file.getName().endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    private static void logTransferBytes(File[] files){
        long bytes = 0;
        for (File file : files) {
            //Make sure we'll be sending it
            if (!isSupportedFiletype(file)) {
                continue;
            }
            bytes += file.length();
        }

        Log.d(TAG, "Storing " + bytes + " form bytes");
    }

    /**
     * Writes any properties of this form/user the receiving tablet might want to form.properties
     * Current properties:
     *  PostURL:    The receiver will attempt to submit to this URL instead of its default URL.
     *              We do this because HQ uses the receiver URL to help display forms prettily.
     * @param formInstanceFolder: the form instance folder to write in
     */
    private void writeProperties(File formInstanceFolder) {
        FileOutputStream outputStream = null;
        try {
            File formProperties = new File(formInstanceFolder, "form.properties");
            outputStream = new FileOutputStream(formProperties);
            Properties properties = new Properties();
            SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
            // HQ likes us to submit forms to the "correct" app and user specific URL
            String postUrl = settings.getString(CommCarePreferences.PREFS_SUBMISSION_URL_KEY,
                    c.getString(R.string.PostURL));
            properties.setProperty("PostURL", postUrl);
            properties.store(outputStream, null);
        } catch(IOException e){
            // we'll just ignore this, not the end of the world
            e.printStackTrace();
        } finally{
            if(outputStream != null){
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected Pair<Long, FormRecord[]> doTaskBackground(String... params) {
        Log.d(TAG, "Doing zip task in background with params: " + Arrays.toString(params));

        Long[] results;
        // we want this directory to be clean
        if (storedFormDirectory.exists()) {
            storedFormDirectory.delete();
        }
        storedFormDirectory.mkdirs();

        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        Vector<Integer> ids = StorageUtils.getUnsentOrUnprocessedFormsForCurrentApp(storage);

        if (ids.size() > 0) {
            FormRecord[] records = new FormRecord[ids.size()];
            results = new Long[records.length];
            for (int i = 0; i < ids.size(); ++i) {
                records[i] = storage.read(ids.elementAt(i));
                results[i] = FormUploadUtil.FAILURE;
            }

            publishProgress(Localization.get("bulk.form.start"));

            for (int i = 0; i < records.length; ++i) {
                FormRecord record = records[i];
                try {
                    //If it's unsent, go ahead and send it
                    if (FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
                        File folder;
                        try {
                            folder = new File(record.getPath(c)).getCanonicalFile().getParentFile();
                        } catch (IOException e) {
                            Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Bizarre. Exception just getting the file reference. Not removing." + getExceptionText(e));
                            continue;
                        }

                        //Good!
                        //Time to transfer forms to storage!
                        results[i] = copyFileInstanceFromStorage(folder, new SecretKeySpec(record.getAesKey(), "AES"));
                        if (results[i].intValue() == FormUploadUtil.FAILURE) {
                            publishProgress("Failure during zipping process");
                            return null;
                        }
                    }
                } catch (Exception e) {
                    //Just try to skip for now. Hopefully this doesn't wreck the model :/
                    Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Totally Unexpected Error during form submission" + getExceptionText(e));
                }
            }

            long result = getLoopResult(results);

            return new Pair<>(result, records);

        } else {
            publishProgress(Localization.get("form.transfer.no.forms"));
            return null;
        }
    }

    /**
     * Iterate over each form transfer result and return the "worst" (high value) outcome
     * @return The FormUploadUtil int outcome code corresponding to the "worst" result
     */
    private long getLoopResult(Long[] results){
        long returnResult = 0;
        for (long iterResult: results) {
            if (iterResult > returnResult) {
                returnResult = iterResult;
            }
        }
        return returnResult;
    }

    private static String getExceptionText(Exception e) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(bos));
            return new String(bos.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
    }

}

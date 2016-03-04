package org.commcare.tasks;

import android.content.Context;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareWiFiDirectActivity;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.ProcessIssues;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author wspride
 */
public abstract class ZipTask extends CommCareTask<String, String, Integer, CommCareWiFiDirectActivity> {
    private static final String TAG = ZipTask.class.getSimpleName();

    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_FAILURE = -1;

    private Context c;
    // this is where the forms that have been pulled from FormRecord storage to the file system live
    private File storedFormDirectory = new File(CommCareWiFiDirectActivity.toBeTransferredDirectory);

    public final static String FORM_PROPERTIES_FILE = "form.properties";
    public final static String FORM_PROPERTY_POST_URL = "PostURL";

    public static final int ZIP_TASK_ID = 72135;

    public ZipTask(Context c) {
        this.c = c;
        taskId = ZIP_TASK_ID;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(Integer result) {
        super.onPostExecute(result);
        //These will never get Zero'd otherwise
        c = null;
    }

    private boolean zipParentFolder(File toBeZippedDirectory, String zipFilePath) throws IOException {

        Log.d(TAG, "Zipping directory" + toBeZippedDirectory.toString() + " to path " + zipFilePath);

        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFilePath)));

        try {
            if (!toBeZippedDirectory.isDirectory()) {
                throw new RuntimeException("toBeZippedDirecory was not a directory. Bad.");
            }
            // the to be zipped directory should contain a bunch of sub directories
            File[] formInstanceFolders = toBeZippedDirectory.listFiles();

            for (File formInstanceFolder : formInstanceFolders) {
                File[] subFileArray = formInstanceFolder.listFiles();
                zipInstanceFolder(subFileArray, zipFilePath, out);
            }

        } finally {
            out.close();
        }
        return false;
    }

    private void zipInstanceFolder(File[] toBeZippedFiles, String zipFilePath, ZipOutputStream zos)
            throws IOException {
        Log.d(TAG, "Zipping instance folder with files: " + Arrays.toString(toBeZippedFiles)
                + ", zipFilePath: " + zipFilePath);

        int BUFFER_SIZE = 1024;
        BufferedInputStream origin;

        byte data[] = new byte[BUFFER_SIZE];

        for (File file : toBeZippedFiles) {
            FileInputStream fi = new FileInputStream(file);
            origin = new BufferedInputStream(fi, BUFFER_SIZE);
            try {

                String tempPath = file.getPath();

                Log.d(TAG, "Zipping instance folder with path: " + tempPath);

                String[] pathParts = tempPath.split("/");

                int pathPartsLength = pathParts.length;

                String fileName = pathParts[pathPartsLength - 1];
                String fileFolder = pathParts[pathPartsLength - 2];

                Log.d(TAG, "Zipping instance folder with path: " + fileFolder + "/" + fileName);

                ZipEntry entry = new ZipEntry(fileFolder + "/" + fileName);
                zos.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                    zos.write(data, 0, count);
                }
            } finally {
                origin.close();
            }
        }
    }

    @Override
    protected Integer doTaskBackground(String... params) {
        Log.d(TAG, "Doing zip task in background with params: " + params);

        try {
            String zipPath = CommCareWiFiDirectActivity.toBeTransferredDirectory;
            File nf = new File(zipPath);
            if (nf.exists()) {
                nf.delete();
            }
            zipParentFolder(nf, CommCareWiFiDirectActivity.sourceZipDirectory);
            storedFormDirectory.delete();
        } catch (IOException ioe) {
            Log.d(TAG, "IOException: " + ioe.getMessage());
            return RESULT_FAILURE;

        }
        return RESULT_SUCCESS;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
    }

}

package org.commcare.tasks;

import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareWiFiDirectActivity;
import org.commcare.logging.AndroidLogger;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.FileUtil;
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
 *
 * This task zips the files in toBeZippedPath into the file specified by zipFilePath
 * Returns 1 for success, -1 for failure.
 */
public abstract class ZipTask extends CommCareTask<Void, String, ZipTask.ZipTaskResult, CommCareWiFiDirectActivity> {
    private static final String TAG = AndroidLogger.TYPE_FORM_DUMP;

    public enum ZipTaskResult {
        Success,
        Failure
    }

    // this is where the forms that have been pulled from FormRecord storage to the file system live

    public final static String FORM_PROPERTIES_FILE = "form.properties";
    public final static String FORM_PROPERTY_POST_URL = "PostURL";

    public static final int ZIP_TASK_ID = 72135;

    private File toBeZippedFile;
    private String zipFilePath;

    public ZipTask(String toBeZippedPath, String zipFilePath) {
        taskId = ZIP_TASK_ID;
        this.zipFilePath = zipFilePath;
        this.toBeZippedFile = new File(toBeZippedPath);
    }

    private static boolean zipParentFolder(File toBeZippedDirectory, String zipFilePath) throws IOException {

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
                Log.d(TAG, "Zipping instance folder with files: " + Arrays.toString(subFileArray)
                    + ", zipFilePath: " + zipFilePath);
                zipInstanceFolder(subFileArray, out);
            }
            out.finish();
        } finally {
            out.close();
        }
        return false;
    }

    private static void zipInstanceFolder(File[] toBeZippedFiles, ZipOutputStream zos)
            throws IOException {

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

                String filePath = pathParts[pathPartsLength -1] + "/" + pathParts[pathPartsLength - 2];
                Log.d(TAG, "Zipping instance folder with path: " + filePath);

                ZipEntry entry = new ZipEntry(filePath);
                zos.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                    zos.write(data, 0, count);
                }
                zos.closeEntry();
            } finally {
                origin.close();
            }
        }
    }

    @Override
    protected ZipTaskResult doTaskBackground(Void... params) {
        Log.d(TAG, "Doing UnzipTask");
        try {
            FileUtil.deleteFileOrDir(toBeZippedFile);
            zipParentFolder(toBeZippedFile, zipFilePath);
            FileUtil.deleteFileOrDir(toBeZippedFile);
        } catch (IOException ioe) {
            Log.d(TAG, "IOException: " + ioe.getMessage());
            return ZipTaskResult.Failure;

        }
        return ZipTaskResult.Success;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
    }

}

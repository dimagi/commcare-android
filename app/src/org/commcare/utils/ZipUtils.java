package org.commcare.utils;

import org.commcare.activities.CommCareActivity;
import org.commcare.tasks.UnzipTask;

public class ZipUtils {
    /**
     * Starts a Unzip Task for a file at filepath
     * @param activity Activity the unzip task should be connected to
     * @param filePath Path for the file that needs to be unzipped
     * @param targetPath Defines where to extract the zip file
     */
    public static void UnzipFile(CommCareActivity activity, String filePath, String targetPath) {
        FileUtil.deleteFileOrDir(targetPath);
        UnzipTask unzipTask = new UnzipTask();
        unzipTask.connect(activity);
        unzipTask.executeParallel(new String[]{filePath, targetPath});
    }
}

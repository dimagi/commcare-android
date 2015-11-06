package org.odk.collect.android.application;

import android.os.Environment;

import java.io.File;

public class ODKStorage {
    // Storage paths
    public static final String ODK_ROOT = Environment.getExternalStorageDirectory() + "/odk";
    public static final String FORMS_PATH = ODK_ROOT + "/forms";
    public static final String INSTANCES_PATH = ODK_ROOT + "/instances";
    public static final String CACHE_PATH = ODK_ROOT + "/.cache";
    public static final String METADATA_PATH = ODK_ROOT + "/metadata";
    public static final String TMPFILE_PATH = CACHE_PATH + "/tmp.jpg";
    public static final String TMPDRAWFILE_PATH = CACHE_PATH + "/tmpDraw.jpg";

    public static final String DEFAULT_FONTSIZE = "21";

    /**
     * Creates required directories on the SDCard (or other external storage)
     *
     * @throws RuntimeException if there is no SDCard or the directory exists as a non directory
     */
    public static void createODKDirs() throws RuntimeException {
        String cardstatus = Environment.getExternalStorageState();
        if (cardstatus.equals(Environment.MEDIA_REMOVED)
                || cardstatus.equals(Environment.MEDIA_UNMOUNTABLE)
                || cardstatus.equals(Environment.MEDIA_UNMOUNTED)
                || cardstatus.equals(Environment.MEDIA_MOUNTED_READ_ONLY)
                || cardstatus.equals(Environment.MEDIA_SHARED)) {
            throw new RuntimeException("CC reports :: SDCard error: "
                    + Environment.getExternalStorageState());
        }

        String[] dirs = {
                ODK_ROOT, FORMS_PATH, INSTANCES_PATH, CACHE_PATH, METADATA_PATH
        };

        for (String dirName : dirs) {
            File dir = new File(dirName);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    RuntimeException e =
                            new RuntimeException("CC reports :: Cannot create directory: " + dirName);
                    throw e;
                }
            } else {
                if (!dir.isDirectory()) {
                    throw new RuntimeException("CC reports :: " + dirName
                            + " exists, but is not a directory");
                }
            }
        }
    }
}

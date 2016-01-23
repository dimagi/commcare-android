package org.commcare.android.tasks;

import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.FileUtil;
import org.javarosa.core.services.locale.Localization;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class MultimediaInflaterTask<R> extends CommCareTask<String, String, Boolean, R> {

    protected Boolean doTaskBackground(String... params) {
        File archive = new File(params[0]);
        File destination = new File(params[1]);

        int count = 0;
        ZipFile zipfile;
        //From stackexchange
        try {
            zipfile = new ZipFile(archive);
        } catch (IOException ioe) {
            publishProgress(Localization.get("mult.install.bad"));
            return false;
        }
        for (Enumeration e = zipfile.entries(); e.hasMoreElements(); ) {
            count++;
            ZipEntry entry = (ZipEntry)e.nextElement();

            if (entry.isDirectory()) {
                FileUtil.createFolder(new File(destination, entry.getName()).toString());
                //If it's a directory we can move on to the next one
                continue;
            }

            File outputFile = new File(destination, entry.getName());
            if (!outputFile.getParentFile().exists()) {
                FileUtil.createFolder(outputFile.getParentFile().toString());
            }
            if (outputFile.exists()) {
                //Try to overwrite if we can
                if (!outputFile.delete()) {
                    //If we couldn't, just skip for now
                    continue;
                }
            }
            BufferedInputStream inputStream;
            try {
                inputStream = new BufferedInputStream(zipfile.getInputStream(entry));
            } catch (IOException ioe) {
                this.publishProgress(Localization.get("mult.install.progress.badentry", new String[]{entry.getName()}));
                return false;
            }

            BufferedOutputStream outputStream;
            try {
                outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
            } catch (IOException ioe) {
                this.publishProgress(Localization.get("mult.install.progress.baddest", new String[]{outputFile.getName()}));
                return false;
            }

            try {
                try {
                    AndroidStreamUtil.writeFromInputToOutput(inputStream, outputStream);
                } catch (IOException ioe) {
                    this.publishProgress(Localization.get("mult.install.progress.errormoving"));
                    return false;
                }
            } finally {
                try {
                    outputStream.close();
                } catch (IOException ioe) {
                }
                try {
                    inputStream.close();
                } catch (IOException ioe) {
                }
            }
        }


        return true;
    }
}

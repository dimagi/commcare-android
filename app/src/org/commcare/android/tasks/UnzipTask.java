package org.commcare.android.tasks;

import android.util.Log;

import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author ctsims
 */
public abstract class UnzipTask<R> extends CommCareTask<String, String, Integer, R> {
        
        public static final int UNZIP_TASK_ID = 7212435;
        
        public File mFile;
        
        public UnzipTask() {
            Log.d(CommCareWiFiDirectActivity.TAG, "UnZip task constructor");
            this.taskId = UNZIP_TASK_ID;

            TAG = UnzipTask.class.getSimpleName();
        }

        @Override
        protected Integer doTaskBackground(String... params) {
            
            Logger.log(CommCareWiFiDirectActivity.TAG, "Doing unzip task");
            
            Log.d(CommCareWiFiDirectActivity.TAG, "Task in background");
            
            File archive = new File(params[0]);
            File destination = new File(params[1]);
            
            Log.d(CommCareWiFiDirectActivity.TAG, "Doing task in background with archive: " + archive + " destination: "+ destination);
            
            int count = 0;
            ZipFile zipfile;
            //From stackexchange
            try {
                zipfile = new ZipFile(archive);
            } catch(IOException ioe) {
                Log.d(CommCareWiFiDirectActivity.TAG, "IOException 4");
                publishProgress("Could not find target file for unzipping.");
                return -1;
            }
            for (Enumeration e = zipfile.entries(); e.hasMoreElements();) {
                Log.d(CommCareWiFiDirectActivity.TAG, "In UnZip Loop");
                Localization.get("mult.install.progress", new String[] {String.valueOf(count)});
                count++;
                ZipEntry entry = (ZipEntry) e.nextElement();
                
                if (entry.isDirectory()) {
                    FileUtil.createFolder(new File(destination, entry.getName()).toString());
                    //If it's a directory we can move on to the next one
                    continue;
                }

                File outputFile = new File(destination, entry.getName());
                if (!outputFile.getParentFile().exists()) {
                    FileUtil.createFolder(outputFile.getParentFile().toString());
                }
                if(outputFile.exists()) {
                    //Try to overwrite if we can
                    if(!outputFile.delete()) {
                        //If we couldn't, just skip for now
                        continue;
                    }
                }
                BufferedInputStream inputStream;
                try {
                    inputStream = new BufferedInputStream(zipfile.getInputStream(entry));
                } catch(IOException ioe) {
                    Log.d(CommCareWiFiDirectActivity.TAG, "IOEXception 0");
                    this.publishProgress(Localization.get("mult.install.progress.badentry", new String[] {entry.getName()}));
                    return -1;
                }
                
                BufferedOutputStream outputStream;
                try {
                    outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
                } catch(IOException ioe) {
                    Log.d(CommCareWiFiDirectActivity.TAG, "IOException 1");
                    this.publishProgress(Localization.get("mult.install.progress.baddest", new String[] {outputFile.getName()}));
                    return -1;
                }

                try {
                    try {
                        AndroidStreamUtil.writeFromInputToOutput(inputStream, outputStream);
                    } catch(IOException ioe) {
                        Log.d(CommCareWiFiDirectActivity.TAG, "IOException 2");
                        this.publishProgress(Localization.get("mult.install.progress.errormoving"));
                        return -1;
                    }
                } finally {
                    try {
                    outputStream.close();
                    } catch(IOException ioe) {}
                    try {
                    inputStream.close();
                    } catch(IOException ioe) {}
                }
            }
            
            Log.d(CommCareWiFiDirectActivity.TAG, "Exited Loop");
            Logger.log(CommCareWiFiDirectActivity.TAG, "Successfully unzipped files");
            
            return count;
        }
}

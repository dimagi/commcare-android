package org.commcare.tasks;

import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.FileUtil;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.views.notifications.ProcessIssues;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.Properties;

/**
 * @author ctsims
 */
public abstract class SendTask<R> extends CommCareTask<Void, String, Boolean, R> {
    private String url;
    private Long[] results;

    private final File dumpDirectory;

    private static final String MALFORMED_FILE_CATEGORY = "malformed-file";

    public static final int BULK_SEND_ID = 12335645;

    // 5MB less 1KB overhead

    public SendTask(String url, File dumpDirectory) {
        this.url = url;
        this.taskId = SendTask.BULK_SEND_ID;
        this.dumpDirectory = dumpDirectory;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        //These will never get Zero'd otherwise
        url = null;
        results = null;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
    }

    @Override
    protected Boolean doTaskBackground(Void... params) {

        publishProgress(Localization.get("bulk.form.send.start"));

        //sanity check
        if (!(dumpDirectory.isDirectory())) {
            return false;
        }

        File[] files = dumpDirectory.listFiles();
        int counter = 0;

        results = new Long[files.length];

        for (int i = 0; i < files.length; ++i) {
            //Assume failure
            results[i] = FormUploadUtil.FAILURE;
        }

        boolean allSuccessful = true;

        for (int i = 0; i < files.length; i++) {

            publishProgress(Localization.get("bulk.send.dialog.progress", new String[]{"" + (i + 1)}));

            File formFolder = files[i];

            if (!(formFolder.isDirectory())) {
                Log.e("send", "Encountered non form entry in file dump folder at path: " + formFolder.getAbsolutePath());
                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Send_MalformedFile, new String[]{null, formFolder.getName()}, MALFORMED_FILE_CATEGORY));
                continue;
            }
            try {

                // see if we have a form.properties file to load the PostURL from
                FilenameFilter filter = new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        return filename.equals(ZipTask.FORM_PROPERTIES_FILE);
                    }
                };
                // there should only be one of these
                File[] formPropertiesFile = formFolder.listFiles(filter);
                if(formPropertiesFile != null && formPropertiesFile.length > 0){
                    Properties properties = FileUtil.loadProperties(formPropertiesFile[0]);
                    if(properties != null && properties.getProperty(ZipTask.FORM_PROPERTY_POST_URL) != null){
                        url = properties.getProperty(ZipTask.FORM_PROPERTY_POST_URL);
                        Logger.log(TAG, "Successfully got form.property PostURL: " + url);
                    }
                    // don't submit this file
                    FileUtil.deleteFileOrDir(formPropertiesFile[0]);
                }

                User user = CommCareApplication._().getSession().getLoggedInUser();
                results[i] = FormUploadUtil.sendInstance(counter, formFolder, url, user);

                if (results[i] == FormUploadUtil.FULL_SUCCESS) {
                    FileUtil.deleteFileOrDir(formFolder);
                } else if (results[i] == FormUploadUtil.TRANSPORT_FAILURE) {
                    allSuccessful = false;
                    publishProgress(Localization.get("bulk.send.transport.error"));
                    return false;
                } else {
                    allSuccessful = false;
                    CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Send_MalformedFile, new String[]{null, formFolder.getName()}, MALFORMED_FILE_CATEGORY));
                    publishProgress(Localization.get("bulk.send.file.error", new String[]{formFolder.getAbsolutePath()}));
                }
                counter++;
            } catch (SessionUnavailableException | FileNotFoundException fe) {
                Log.e("E", Localization.get("bulk.send.file.error", new String[]{formFolder.getAbsolutePath()}), fe);
                publishProgress(Localization.get("bulk.send.file.error", new String[]{fe.getMessage()}));
            }
        }
        return allSuccessful;
    }
}


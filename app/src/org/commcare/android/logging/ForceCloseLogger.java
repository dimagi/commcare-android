package org.commcare.android.logging;

import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.AndroidLogSerializer;
import org.commcare.logging.DeviceReportWriter;
import org.commcare.models.database.SqlStorage;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.preferences.CommCareServerPreferences;
import org.javarosa.core.model.User;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Date;

/**
 * Catch exceptions that are going to crash the phone, grab the stack trace,
 * and upload to device logs.
 *
 * @author csims@dimagi.com
 **/
public class ForceCloseLogger {
    private static final String TAG = ForceCloseLogger.class.getSimpleName();

    private static SqlStorage<ForceCloseLogEntry> logStorage;

    public static void registerStorage(SqlStorage<ForceCloseLogEntry> storage) {
        logStorage = storage;
    }

    public static void reportExceptionInBg(final Throwable exception) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                sendToServerOrStore(exception);
            }
        }).start();
    }

    /**
     * Attempts to send a force close report for the given exception to the server
     * immediately. If this fails, instead writes the exception to storage as a ForceCloseLogEntry,
     * so that we can attempt to send it again later during a normal LogSubmissionTask
     */
    private static void sendToServerOrStore(Throwable exception) {
        ByteArrayOutputStream streamToWriteErrorTo = new ByteArrayOutputStream();
        String exceptionText = getStackTrace(exception);
        String submissionUri = getSubmissionUri();
        ForceCloseLogEntry entry = new ForceCloseLogEntry(exceptionText);

        try {
            DeviceReportWriter reportWriter = new DeviceReportWriter(streamToWriteErrorTo);
            reportWriter.addReportElement(new ForceCloseLogSerializer(entry));
            // TEMPORARILY write this in the old format as well, until HQ starts parsing the new one
            reportWriter.addReportElement(new AndroidLogSerializer<ForceCloseLogEntry>(entry));
            reportWriter.write();
            if (!sendErrorToServer(streamToWriteErrorTo.toByteArray(), submissionUri)) {
                writeErrorToStorage(entry);
            }
        } catch (IOException e) {
            e.printStackTrace();
            // Couldn't create a report writer, so just manually create the data we want to send
            String fsDate = new Date().toString();
            byte[] data = ("<?xml version='1.0' ?><n0:device_report xmlns:n0=\"http://code.javarosa.org/devicereport\"><device_id>FAILSAFE</device_id><report_date>" + fsDate + "</report_date><log_subreport><log_entry date=\"" + fsDate + "\"><entry_type>forceclose</entry_type><entry_message>" + exceptionText + "</entry_message></log_entry></log_subreport></device_report>").getBytes();
            if (!sendErrorToServer(data, submissionUri)) {
                writeErrorToStorage(entry);
            }
        }
    }

    /**
     * Try to send the given data to the given uri
     *
     * @return If send was successful
     */
    private static boolean sendErrorToServer(byte[] dataToSend, String submissionUri) {
        String payload = new String(dataToSend);
        Log.d(TAG, "Outgoing payload: " + payload);

        MultipartEntity entity = new MultipartEntity();
        try {
            //Apparently if you don't have a filename in the multipart wrapper, some receivers
            //don't properly receive this post.
            StringBody body = new StringBody(payload, "text/xml", MIME.DEFAULT_CHARSET) {
                @Override
                public String getFilename() {
                    return "exceptionreport.xml";
                }
            };
            entity.addPart("xml_submission_file", body);
        } catch (IllegalCharsetNameException | UnsupportedEncodingException
                | UnsupportedCharsetException e1) {
            e1.printStackTrace();
            return false;
        }

        HttpRequestGenerator generator;
        try {
            User user = CommCareApplication._().getSession().getLoggedInUser();
            generator = new HttpRequestGenerator(user);
        } catch (Exception e) {
            generator = HttpRequestGenerator.buildNoAuthGenerator();
        }

        try {
            HttpResponse response = generator.postData(submissionUri, entity);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            response.getEntity().writeTo(bos);
            Log.d(TAG, "Response: " + new String(bos.toByteArray()));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static void writeErrorToStorage(ForceCloseLogEntry entry) {
        if (logStorage != null) {
            logStorage.write(entry);
        }
    }

    private static String getSubmissionUri() {
        CommCareApp currentApp = CommCareApplication._().getCurrentApp();
        if (currentApp != null) {
            return currentApp.getAppPreferences().getString(
                    CommCareServerPreferences.PREFS_SUBMISSION_URL_KEY,
                    CommCareApplication._().getString(R.string.PostURL));
        } else {
            return CommCareApplication._().getString(R.string.PostURL);
        }
    }

    public static String getStackTrace(Throwable e) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(bos));
        return new String(bos.toByteArray());
    }

    public static String getStackTraceWithContext(Throwable e) {
        String stackTrace = getStackTrace(e);

        if (e.getCause() != null) {
            stackTrace += "Sub Context: \n" + getStackTrace(e.getCause());
        }

        return stackTrace;
    }
}

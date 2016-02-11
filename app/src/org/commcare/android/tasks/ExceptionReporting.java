package org.commcare.android.tasks;

import android.content.SharedPreferences;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.logging.AndroidLogger;
import org.commcare.android.logging.DeviceReportRecord;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.android.logging.ForceCloseLogSerializer;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.model.User;
import org.commcare.android.logging.AndroidLogEntry;
import org.commcare.android.logging.AndroidLogSerializer;
import org.commcare.android.logging.DeviceReportWriter;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Date;

/**
 * Catch exceptions that are going to crash the phone, grab the stack trace,
 * and upload to developers.
 *
 * @author csims@dimagi.com
 **/
public class ExceptionReporting {
    private static final String TAG = ExceptionReporting.class.getSimpleName();

    public static void reportExceptionInBg(final Throwable exception) {
        new Thread(new Runnable() {
            public void run() {
                sendExceptionToServer(exception);
            }
        }).start();
    }

    private static void sendExceptionToServer(Throwable exception) {
        DeviceReportRecord record = null;
        ByteArrayOutputStream baos = null;
        try {
            // We had a session and were able to create a DeviceReportRecord, so will send the error
            // using that. This is preferable when possible, because it means that if the send
            // fails, we have still written the DeviceReportRecord to storage, and will attempt
            // to send it again later
            record = DeviceReportRecord.generateRecordStubForForceCloses();
        } catch (SessionUnavailableException e) {
            // The forceclose occurred when a user was not logged in, so we don't have a session
            // to store the record to; just use a temp output stream instead
            baos = new ByteArrayOutputStream();
        }

        String exceptionText = getStackTrace(exception);
        String submissionUri = getSubmissionUri();
        DeviceReportWriter reportWriter;

        try {
            reportWriter = new DeviceReportWriter(record != null ? record.openOutputStream() : baos);
            reportWriter.addReportElement(new ForceCloseLogSerializer(
                    new ForceCloseLogEntry(exception, exceptionText)));
            reportWriter.write();

            if (record == null) {
                sendWithoutWriting(baos.toByteArray(), submissionUri);
            } else {
                if (!LogSubmissionTask.submitDeviceReportRecord(record, submissionUri, null, -1)) {
                    // If submission failed, write this record to storage so we can send it later
                    try {
                        SqlStorage<DeviceReportRecord> storage =
                                CommCareApplication._().getUserStorage(DeviceReportRecord.class);
                        storage.write(record);
                    } catch (UserStorageClosedException e) {

                    }
                }
            }
        } catch (IOException e) {
            // Couldn't create a report writer, so just manually create the data we want to send
            e.printStackTrace();
            String fsDate = new Date().toString();
            byte[] data = ("<?xml version='1.0' ?><n0:device_report xmlns:n0=\"http://code.javarosa.org/devicereport\"><device_id>FAILSAFE</device_id><report_date>" + fsDate + "</report_date><log_subreport><log_entry date=\"" + fsDate + "\"><entry_type>forceclose</entry_type><entry_message>" + exceptionText + "</entry_message></log_entry></log_subreport></device_report>").getBytes();
            sendWithoutWriting(data, submissionUri);
        }
    }

    private static void sendWithoutWriting(byte[] dataToSend, String submissionUri) {
        //TODO: Send this with the standard logging subsystem
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
        }

        HttpRequestGenerator generator;
        try {
            User user = CommCareApplication._().getSession().getLoggedInUser();
            if (user.getUserType().equals(User.TYPE_DEMO)) {
                generator = new HttpRequestGenerator();
            } else {
                generator = new HttpRequestGenerator(user);
            }
        } catch (Exception e) {
            generator = new HttpRequestGenerator();
        }

        try {
            HttpResponse response = generator.postData(submissionUri, entity);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            response.getEntity().writeTo(bos);
            Log.d(TAG, "Response: " + new String(bos.toByteArray()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getSubmissionUri() {
        try {
            SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
            return settings.getString(CommCarePreferences.PREFS_SUBMISSION_URL_KEY,
                    CommCareApplication._().getString(R.string.PostURL));
        } catch (Exception e) {
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

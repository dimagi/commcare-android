package org.commcare.android.logging;

import android.util.Log;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.AndroidLogSerializer;
import org.commcare.logging.DeviceReportWriter;
import org.commcare.models.database.SqlStorage;
import org.commcare.network.CommcareRequestGenerator;
import org.commcare.preferences.CommCareServerPreferences;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.User;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

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

        List<MultipartBody.Part> parts = new ArrayList<>();
        try {
            //Apparently if you don't have a filename in the multipart wrapper, some receivers
            //don't properly receive this post.
            parts.add(MultipartBody.Part.createFormData(
                    "xml_submission_file",
                    "exceptionreport.xml",
                    RequestBody.create(MediaType.parse("text/xml"), payload)));
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e1) {
            e1.printStackTrace();
            return false;
        }

        CommcareRequestGenerator generator;
        try {
            User user = CommCareApplication.instance().getSession().getLoggedInUser();
            generator = new CommcareRequestGenerator(user);
        } catch (Exception e) {
            generator = CommcareRequestGenerator.buildNoAuthGenerator();
        }

        try {
            Response<ResponseBody> response = generator.postMultipart(submissionUri, parts);
            if (response.body() != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                StreamsUtil.writeFromInputToOutput(response.body().byteStream(), bos);
                Log.d(TAG, "Response: " + new String(bos.toByteArray()));
            }
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
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (currentApp != null) {
            return currentApp.getAppPreferences().getString(
                    CommCareServerPreferences.PREFS_SUBMISSION_URL_KEY,
                    CommCareApplication.instance().getString(R.string.PostURL));
        } else {
            return CommCareApplication.instance().getString(R.string.PostURL);
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

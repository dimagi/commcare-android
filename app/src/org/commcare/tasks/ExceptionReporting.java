package org.commcare.tasks;

import android.content.SharedPreferences;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.logging.AndroidLogEntry;
import org.commcare.logging.AndroidLogSerializer;
import org.commcare.logging.DeviceReportWriter;
import org.commcare.network.HttpRequestGenerator;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        //TODO: This is ridiculous. Just do the normal log submission process
        DeviceReportWriter report;
        try {
            report = new DeviceReportWriter(baos);
        } catch (IOException e) {
            report = null;
        }

        String exceptionText = getStackTrace(exception);
        if (report != null) {
            report.addReportElement(new AndroidLogSerializer(new AndroidLogEntry("forceclose", exceptionText, new Date())));
        }

        byte[] data;
        try {
            if (report == null) {
                throw new IOException();
            }
            report.write();
            data = baos.toByteArray();
        } catch (IOException e) {
            //_weak_
            e.printStackTrace();
            String fsDate = new Date().toString();
            data = ("<?xml version='1.0' ?><n0:device_report xmlns:n0=\"http://code.javarosa.org/devicereport\"><device_id>FAILSAFE</device_id><report_date>" + fsDate + "</report_date><log_subreport><log_entry date=\"" + fsDate + "\"><entry_type>forceclose</entry_type><entry_message>" + exceptionText + "</entry_message></log_entry></log_subreport></device_report>").getBytes();
        }

        String URI = CommCareApplication._().getString(R.string.PostURL);
        try {
            SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
            URI = settings.getString(CommCarePreferences.PREFS_SUBMISSION_URL_KEY, CommCareApplication._().getString(R.string.PostURL));
        } catch (Exception e) {
            //D-oh. Really?
        }

        //TODO: Send this with the standard logging subsystem
        String payload = new String(data);
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
            HttpResponse response = generator.postData(URI, entity);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            response.getEntity().writeTo(bos);
            Log.d(TAG, "Response: " + new String(bos.toByteArray()));
        } catch (IOException e) {
            e.printStackTrace();
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

package org.commcare.android.tasks;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.commcare.android.tasks.templates.CommCareTask;
import org.javarosa.core.services.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Runs various tasks that diagnose problems that a user may be facing in connecting to commcare services.
 * @author srengesh
 */
public abstract class ConnectionDiagnosticTask<R> extends CommCareTask<Void, String, ConnectionDiagnosticTask.Test, R>
{    
    final Context c;

    public enum Test
    {
        isOnline,
        googlePing,
        commCarePing
    }
    
    public static final int CONNECTION_ID = 12335800;
        
    /** Problem reported via connection diagnostic tool **/
    public static final String CONNECTION_DIAGNOSTIC_REPORT = "connection-report";

    
    
    
    
    //strings used to in various diagnostics tests. Change these values if the URLs/HTML code is changed.
    private static final String googleURL = "www.google.com";
    private static final String commcareURL = "http://www.commcarehq.org/serverup.txt";
    private static final String commcareHTML = "success";
    private static final String pingPrefix = "ping -c 1 ";
    
    
    
    //the various log messages that will be returned regarding the outcomes of the tests
    private static final String logNotConnectedMessage = "Network test: Not connected.";
    private static final String logConnectionSuccessMessage = "Network test: Success.";
    
    private static final String logGoogleNullPointerMessage = "Google ping test: Process could not be started.";
    private static final String logGoogleIOErrorMessage = "Google ping test: Local error.";
    private static final String logGoogleInterruptedMessage = "Google ping test: Process was interrupted.";
    private static final String logGoogleSuccessMessage = "Google ping test: Success.";
    private static final String logGoogleUnexpectedResultMessage = "Google ping test: Unexpected HTML Result.";
    
    private static final String logCCIllegalStateMessage = "CCHQ ping test: Illegal state.";
    private static final String logCCNetworkFailureMessge = "CCHQ ping test: Network failure.";
    private static final String logCCIOErrorMessage = "CCHQ ping test: Local error.";
    private static final String logCCUnexpectedResultMessage = "CCHQ ping test: Unexpected HTML result";
    private static final String logCCSuccessMessage = "CCHQ ping test: Success.";
    
    public ConnectionDiagnosticTask(Context c) {
        this.c = c;
        this.taskId = CONNECTION_ID;

        TAG = ConnectionDiagnosticTask.class.getSimpleName();
    }

    @Override
    protected Test doTaskBackground(Void... params) 
    {    
        Test out = null;
        if(!isOnline(this.c))
        {
            out = Test.isOnline;
        }
        else if(!pingSuccess(googleURL))
        {
            out = Test.googlePing;
        }
        else if(!pingCC(commcareURL))
        {
            out = Test.commCarePing;
        }
        return out;
    }
    
    //checks if the network is connected or not.
    private boolean isOnline(Context context) 
    {
        ConnectivityManager conManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conManager.getActiveNetworkInfo();
        boolean notInAirplaneMode = (netInfo != null && netInfo.isConnected());
        
        //if user is not online, log not connected. if online, log success
        String logMessage = !notInAirplaneMode? logNotConnectedMessage : logConnectionSuccessMessage;
        Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logMessage);

        return notInAirplaneMode;
    }

    //check if a ping to a specific ip address (used for google url) is successful.
    private boolean pingSuccess(String url)
    {
        Process pingCommand = null;
        try {
            //append the input url to the ping command
            String pingURL = pingPrefix + url;
            
            //run the ping command at runtime
            pingCommand = java.lang.Runtime.getRuntime().exec(pingURL);
            if(pingCommand == null)
            {
                Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logGoogleNullPointerMessage);
                return false;
            }
        } 
        catch (IOException e) 
        {
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logGoogleIOErrorMessage + System.getProperty("line.separator") + "Stack trace: " + ExceptionReporting.getStackTrace(e));
            return false;
        }
        int pingReturn = Integer.MAX_VALUE;
        try 
        {
            pingReturn = pingCommand.waitFor();
        } 
        catch (InterruptedException e) 
        {
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logGoogleInterruptedMessage + System.getProperty("line.separator") + "Stack trace: " + ExceptionReporting.getStackTrace(e));
            return false;
        } 
        //0 if success, 2 if fail
        String messageOut = pingReturn==0? logGoogleSuccessMessage : logGoogleUnexpectedResultMessage;
        Logger.log(CONNECTION_DIAGNOSTIC_REPORT, messageOut);
        return pingReturn == 0;
    }
    
    private boolean pingCC(String url)
    {        
        //uses HttpClient and HttpGet to read the HTML from the specified url        
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(url);
        InputStream stream = null;
        String htmlLine = null;
        BufferedReader buffer = null;
        InputStreamReader reader = null;
        try {
            //read html using an input stream reader
            stream = client.execute(get).getEntity().getContent();
            reader = new InputStreamReader(stream);
            buffer = new BufferedReader(reader);
            //should read "success" if the server is up.
            htmlLine = buffer.readLine();
        } catch (IllegalStateException e) {
            //if a stream to this web address has already been invoked on the same thread
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logCCIllegalStateMessage + System.getProperty("line.separator") + "Stack trace: " + ExceptionReporting.getStackTrace(e));
            return false;
        } catch (ClientProtocolException e) {
            //general HTTP Exception
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logCCNetworkFailureMessge + System.getProperty("line.separator") + "Stack trace: " + ExceptionReporting.getStackTrace(e));
            return false;
        } catch (IOException e) {
            //error on client side
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logCCIOErrorMessage + System.getProperty("line.separator") + "Stack trace: " + ExceptionReporting.getStackTrace(e));
            return false;
        } finally {
            if(buffer != null)
            {
                try {
                    buffer.close();
                } catch (IOException e) {
                    Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logCCIOErrorMessage + System.getProperty("line.separator") + "Stack trace: " + ExceptionReporting.getStackTrace(e));
                    return false;
                }
            }
            if(reader != null)
            {
                try {
                    reader.close();
                } catch (IOException e) {
                    Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logCCIOErrorMessage + System.getProperty("line.separator") + "Stack trace: " + ExceptionReporting.getStackTrace(e));
                    return false;
                }
            }
            if(stream != null)
            {
                try {
                    stream.close();
                } catch (IOException e) {
                    String out = logCCIOErrorMessage + System.getProperty("line.separator") +
                            "Stack trace: " +
                            ExceptionReporting.getStackTrace(e);
                    Logger.log(CONNECTION_DIAGNOSTIC_REPORT, out);
                    return false;
                }
            }
        }
        if(htmlLine.equals(commcareHTML))
        {
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logCCSuccessMessage);
            return true;
        }
        else
        {
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, logCCUnexpectedResultMessage);
            return false;
        }
    }    
}

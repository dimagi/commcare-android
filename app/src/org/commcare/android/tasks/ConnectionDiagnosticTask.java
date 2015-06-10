package org.commcare.android.tasks;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.Logger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Runs various tasks that diagnose problems that a user may be facing in connecting to commcare services.
 * @author srengesh
 */



//CommCareTask<A, B, C, R>
public abstract class ConnectionDiagnosticTask<R> extends CommCareTask<Void, String, ConnectionDiagnosticTask.Test, R>
{    
    Context c;
    CommCarePlatform platform;
    
    public static enum Test
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
    
    public ConnectionDiagnosticTask(Context c, CommCarePlatform platform) {
        this.c = c;
        this.platform = platform;
        this.taskId = CONNECTION_ID;
    }
    
    //onProgressUpdate(<B>)
    
    //onPostExecute(<C>)
    
    //onCancelled()

    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTask#doTaskBackground(java.lang.Object[])
     * 
     * doTaskBackground(<A>) returns <C>
     */
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
        
        if(notInAirplaneMode)
        {
            return true;
        }
        return false;
    }

    //check if a ping to a specific ip address (used for google url) is successful.
    private boolean pingSuccess(String url)
    {
        Process pingCommand = null;
        try {
            //append the input url to the ping command
            StringBuilder pingURLBuilder = new StringBuilder(pingPrefix);
            pingURLBuilder.append(url);
            String pingURL = pingURLBuilder.toString();
            
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
            StringBuilder out = new StringBuilder(logGoogleIOErrorMessage);
            out.append(System.getProperty("line.separator"));
            out.append("Stack trace: ");
            out.append(ExceptionReportTask.getStackTrace(e));
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, out.toString());
            return false;
        }
        int pingReturn = Integer.MAX_VALUE;
        try 
        {
            pingReturn = pingCommand.waitFor();
        } 
        catch (InterruptedException e) 
        {
            StringBuilder out = new StringBuilder(logGoogleInterruptedMessage);
            out.append(System.getProperty("line.separator"));
            out.append("Stack trace: ");
            out.append(ExceptionReportTask.getStackTrace(e));
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, out.toString());
            return false;
        } 
        //0 if success, 2 if fail
        String messageOut = pingReturn==0? logGoogleSuccessMessage : logGoogleUnexpectedResultMessage;
        Logger.log(CONNECTION_DIAGNOSTIC_REPORT, messageOut);
        if(pingReturn != 0)
        {
            return false;
        }
        return true;
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
            StringBuilder out = new StringBuilder(logCCIllegalStateMessage);
            out.append(System.getProperty("line.separator"));
            out.append("Stack trace: ");
            out.append(ExceptionReportTask.getStackTrace(e));
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, out.toString());
            return false;
        } catch (ClientProtocolException e) {
            //general HTTP Exception
            StringBuilder out = new StringBuilder(logCCNetworkFailureMessge);
            out.append(System.getProperty("line.separator"));
            out.append("Stack trace: ");
            out.append(ExceptionReportTask.getStackTrace(e));
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, out.toString());
            return false;
        } catch (IOException e) {
            //error on client side
            StringBuilder out = new StringBuilder(logCCIOErrorMessage);
            out.append(System.getProperty("line.separator"));
            out.append("Stack trace: ");
            out.append(ExceptionReportTask.getStackTrace(e));
            Logger.log(CONNECTION_DIAGNOSTIC_REPORT, out.toString());
            return false;
        } finally {
            if(buffer != null)
            {
                try {
                    buffer.close();
                } catch (IOException e) {
                    StringBuilder out = new StringBuilder(logCCIOErrorMessage);
                    out.append(System.getProperty("line.separator"));
                    out.append("Stack trace: ");
                    out.append(ExceptionReportTask.getStackTrace(e));
                    Logger.log(CONNECTION_DIAGNOSTIC_REPORT, out.toString());
                    return false;
                }
            }
            if(reader != null)
            {
                try {
                    reader.close();
                } catch (IOException e) {
                    StringBuilder out = new StringBuilder(logCCIOErrorMessage);
                    out.append(System.getProperty("line.separator"));
                    out.append("Stack trace: ");
                    out.append(ExceptionReportTask.getStackTrace(e));
                    Logger.log(CONNECTION_DIAGNOSTIC_REPORT, out.toString());
                    return false;
                }
            }
            if(stream != null)
            {
                try {
                    stream.close();
                } catch (IOException e) {
                    StringBuilder out = new StringBuilder(logCCIOErrorMessage);
                    out.append(System.getProperty("line.separator"));
                    out.append("Stack trace: ");
                    out.append(ExceptionReportTask.getStackTrace(e));
                    Logger.log(CONNECTION_DIAGNOSTIC_REPORT, out.toString());
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

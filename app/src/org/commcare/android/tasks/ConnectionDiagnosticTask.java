package org.commcare.android.tasks;
import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.commcare.android.util.Tuple;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.locale.Localization;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Runs various tasks that diagnose problems that a user may be facing in connecting to commcare services.
 * @author srengesh
 */

//CommCareTask<A, B, C, R>
public abstract class ConnectionDiagnosticTask<R> extends CommCareTask<Void, String, ArrayList<Tuple<Boolean, String>>, R>
{	
	Context c;
	CommCarePlatform platform;
	
	//result of network test
	public static Boolean isOnline;
	
	public static final int CONNECTION_ID = 12335800;
	
	//strings used to in various diagnostics tests. Change these values if the URLs/HTML code is changed.
	public static final String googleURL = "www.google.com";
	public static final String commcareURL = "http://www.commcarehq.org/serverup.txt";
	public static final String commcareHTML = "success";
	public static final String pingPrefix = "ping -c 1 ";
	
	
	
	//the various log messages that will be returned regarding the outcomes of the tests
	public static final String logNotConnectedMessage = "Network test: Not connected.";
	public static final String logConnectionSuccessMessage = "Network test: Success.";
	
	public static final String logGoogleNullPointerMessage = "Google ping test: Process could not be started.";
	public static final String logGoogleIOErrorMessage = "Google ping test: Local error.";
	public static final String logGoogleInterruptedMessage = "Google ping test: Process was interrupted.";
	public static final String logGoogleSuccessMessage = "Google ping test: Success.";
	public static final String logGoogleUnexpectedResultMessage = "Google ping test: Unexpected HTML Result.";
	
	public static final String logCCIllegalStateMessage = "CCHQ ping test: Illegal state.";
	public static final String logCCNetworkFailureMessge = "CCHQ ping test: Network failure.";
	public static final String logCCIOErrorMessage = "CCHQ ping test: Local error.";
	public static final String logCCUnexpectedResultMessage = "CCHQ ping test: Unexpected HTML result";
	public static final String logCCSuccessMessage = "CCHQ ping test: Success.";
	
	public ConnectionDiagnosticTask(Context c, CommCarePlatform platform) throws SessionUnavailableException
	{
		this.c = c;
		this.platform = platform;
		this.taskId = CONNECTION_ID;
	}
	
	//onProgressUpdate(<B>)
	
	//onPostExecute(<C>)
	
	//onCancelled()

	
	//doTaskBackground(<A>) returns <C>
	@Override
	protected ArrayList<Tuple<Boolean, String>> doTaskBackground(Void... params) 
	{	
		ArrayList<Tuple<Boolean, String>> out = new ArrayList<Tuple<Boolean, String>>();
		out.add(isOnline(this.c));
		out.add(pingSuccess(googleURL));
		out.add(pingCC(commcareURL));		
		return out;
	}
	
	//checks if the network is connected or not.
	private static Tuple<Boolean, String> isOnline(Context context) 
	{
		ConnectivityManager conManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = conManager.getActiveNetworkInfo();
		isOnline = (netInfo != null && netInfo.isConnected());
		
		//if user is not online, log not connected. if online, log success
		String logMessage = !isOnline? logNotConnectedMessage : logConnectionSuccessMessage;
		return new Tuple<Boolean, String>(isOnline, logMessage);
	}

	//check if a ping to a specific ip address (used for google url) is successful.
	private static Tuple<Boolean, String> pingSuccess(String url)
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
				return new Tuple<Boolean, String>(false, logGoogleNullPointerMessage);
			}
		} 
		catch (IOException e) 
		{
			return new Tuple<Boolean, String>(false, logGoogleIOErrorMessage );
		}
		int pingReturn = Integer.MAX_VALUE;
		try 
		{
			pingReturn = pingCommand.waitFor();
		} 
		catch (InterruptedException e) 
		{
			return new Tuple<Boolean, String>(false, logGoogleInterruptedMessage);
		} 
		//0 if success, 2 if fail
		String messageOut = pingReturn==0? logGoogleSuccessMessage : logGoogleUnexpectedResultMessage;
		return new Tuple<Boolean, String>(pingReturn == 0, messageOut);
	}
	
	private static Tuple<Boolean, String> pingCC(String url)
	{
		//uses HttpClient and HttpGet to read the HTML from the specified url
		HttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet(url);
		InputStream stream = null;
		String htmlLine = null;
		try {
			//read html using an input stream reader
			stream = client.execute(get).getEntity().getContent();
			InputStreamReader reader = new InputStreamReader(stream);
			BufferedReader buffer = new BufferedReader(reader);
			//should read "success" if the server is up.
			htmlLine = buffer.readLine();
			buffer.close();
			reader.close();
			stream.close();
		} catch (IllegalStateException e) {
			//if a stream to this web address has already been invoked on the same thread
			return new Tuple<Boolean, String>(false, logCCIllegalStateMessage);
		} catch (ClientProtocolException e) {
			//general HTTP Exception
			return new Tuple<Boolean, String>(false, logCCNetworkFailureMessge);
		} catch (IOException e) {
			//error on client side
			return new Tuple<Boolean, String>(false, logCCIOErrorMessage);
		}
		
		return htmlLine.equals(commcareHTML)? new Tuple<Boolean, String>(true, logCCSuccessMessage) 
									   		: new Tuple<Boolean, String>(false, logCCUnexpectedResultMessage);
	}
	
}

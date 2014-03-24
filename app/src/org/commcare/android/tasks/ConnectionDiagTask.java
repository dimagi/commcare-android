package org.commcare.android.tasks;
import java.io.File;

import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ProcessAndSendTask.ProcessIssues;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public abstract class ConnectionDiagTask<R> extends CommCareTask<Void, String, String, R>
{	
	Context c;
	CommCarePlatform platform;
	
	public static final int CONNECTION_ID = 12335800;
	
	public ConnectionDiagTask(Context c, CommCarePlatform platform) throws SessionUnavailableException
	{
		this.c = c;
		this.platform = platform;
		this.taskId = this.CONNECTION_ID;
	}
	
	@Override
	protected void onProgressUpdate(String... values) 
	{
		super.onProgressUpdate(values);
	}
	
	@Override
	protected void onPostExecute(String result) 
	{
		System.out.println(result);
		super.onPostExecute(result);
//		System.out.println("print this out");
//		System.out.println(result);
//		super.onPostExecute(result);
//		System.out.println(c);
//		System.out.println("come on!");
//		c = null;
	}
	
	@Override
	protected void onCancelled() 
	{
		super.onCancelled();
	}
	
	@Override
	protected String doTaskBackground(Void... params) 
	{
		
		if(isOnline(this.c))
		{
			return "You are connected.";
		}
		return "You are not connected.";
	}
	
	//checks if the network is connected or not.
	public static boolean isOnline(Context context) {
	      ConnectivityManager conManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	      NetworkInfo netInfo = conManager.getActiveNetworkInfo();
	      return (netInfo != null && netInfo.isConnected());
	}
}

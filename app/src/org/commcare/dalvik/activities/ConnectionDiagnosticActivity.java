package org.commcare.dalvik.activities;

import java.util.Map;
import org.commcare.android.util.Tuple;

import java.util.ArrayList;


import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.ConnectionDiagnosticTask;
import org.commcare.android.tasks.DumpTask;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.LogSubmissionTask;
import org.commcare.android.tasks.SendTask;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity that will diagnose various connection problems that a user may be facing.
 * @author srengesh
 *
 */

@ManagedUi(R.layout.connection_diagnostic)
public class ConnectionDiagnosticActivity extends CommCareActivity<ConnectionDiagnosticActivity> {
	

	/** Problem reported via connection diagnostic tool **/
	public static final String CONNECTION_DIAGNOSTIC_REPORT = "connection-report";
	
	@UiElement(R.id.screen_bulk_image1)
	ImageView banner;
	
	@UiElement(value = R.id.connection_test_prompt, locale="connection.test.prompt")
	TextView connectionPrompt;
	
	@UiElement(value = R.id.run_connection_test, locale="connection.test.run")
	Button btnRunTest;
	
	@UiElement(value = R.id.output_message, locale="connection.test.messages")
	TextView txtInteractiveMessages;
	
	@UiElement(value = R.id.settings_button, locale="connection.test.access.settings")
	Button settingsButton;
	
	@UiElement(value = R.id.report_button, locale="connection.test.report.button.message")
	Button reportButton;
		
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		btnRunTest.setOnClickListener(new OnClickListener() 
		{
			@Override
			public void onClick(View v)
			{
				ConnectionDiagnosticTask<ConnectionDiagnosticActivity> mConnectionDiagTask = 
				new ConnectionDiagnosticTask<ConnectionDiagnosticActivity>(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform())
				{	
				@Override
					//<R> receiver, <C> result. 
					//<C> is the return from DoTaskBackground, of type ArrayList<Tuple<Boolean, String>>
					protected void deliverResult(ConnectionDiagnosticActivity receiver, ArrayList<Tuple<Boolean, String>> testResults) 
					{
						for(int i = 0; i < testResults.size(); i++)
						{
							Logger.log(CONNECTION_DIAGNOSTIC_REPORT, testResults.get(i).y);
						}
						
						boolean isConnected = testResults.get(0).x;
						boolean canPingGoogle = testResults.get(1).x;
						//user-caused connection issues
						if(!isConnected || !canPingGoogle)
						{
							//get the appropriate display message based on what the problem is
							String displayMessage = !isConnected? Localization.get("connection.task.internet.fail") 
																: Localization.get("connection.task.remote.ping.fail");
							
							receiver.txtInteractiveMessages.setText(displayMessage);
							receiver.txtInteractiveMessages.setVisibility(View.VISIBLE);
							
							//Set a button that allows you to change your airplane mode settings
							receiver.settingsButton.setOnClickListener( new OnClickListener()
							{
								@Override
								public void onClick(View v)
								{
									startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
								}
							});
							receiver.settingsButton.setVisibility(View.VISIBLE);
						}
						
						//unable to ping commcare -- report this to cchq
						else if(!testResults.get(2).x)
						{
							receiver.txtInteractiveMessages.setText(Localization.get("connection.task.commcare.html.fail"));
							receiver.txtInteractiveMessages.setVisibility(View.VISIBLE);
							receiver.reportButton.setOnClickListener( new OnClickListener()
							{
								@Override
								public void onClick(View v)
								{
					        		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
					        		String url = settings.getString("PostURL", null);
					        		
					        		if(url != null) 
					        		{
						            	LogSubmissionTask reportSubmitter = new LogSubmissionTask(CommCareApplication._(), true, CommCareApplication._().getSession().startDataSubmissionListener(R.string.submission_logs_title), url);
						            	reportSubmitter.execute();
					        		} 
					        		else 
					        		{
					        			//idk what to do here
					        		}
								}
							});
							receiver.reportButton.setVisibility(View.VISIBLE);
						}
						
						//all is well
						else
						{
							receiver.txtInteractiveMessages.setText(Localization.get("connection.task.success"));
							receiver.txtInteractiveMessages.setVisibility(View.VISIBLE);
							receiver.settingsButton.setVisibility(View.INVISIBLE);
							receiver.reportButton.setVisibility(View.INVISIBLE);
						}
					}

					@Override
					protected void deliverUpdate(ConnectionDiagnosticActivity receiver, String... update) 
					{
						receiver.txtInteractiveMessages.setText((Localization.get("connection.test.update.message")));
					}
					
					@Override
					protected void deliverError(ConnectionDiagnosticActivity receiver, Exception e)
					{
						receiver.txtInteractiveMessages.setText(Localization.get("connection.test.error.message"));
						receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
					}
				};
				
				mConnectionDiagTask.connect(ConnectionDiagnosticActivity.this);
				mConnectionDiagTask.execute();				
			}
		});
	}
	
	private AlertDialog newDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle((Localization.get("connection.test.run.title")));
		builder.setMessage(Localization.get("connection.test.now.running"))
		.setCancelable(true);		
		AlertDialog dialog = builder.create();
		return dialog;
	}
	
	@Override
	protected Dialog onCreateDialog(int id) 
	{
		if(id == ConnectionDiagnosticTask.CONNECTION_ID) 
		{
			return newDialog();
		}
		return null;
	}
}

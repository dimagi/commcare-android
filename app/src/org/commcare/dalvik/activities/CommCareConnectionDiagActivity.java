package org.commcare.dalvik.activities;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.ConnectionDiagTask;
import org.commcare.android.tasks.DumpTask;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.SendTask;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author srengesh
 *
 */

@ManagedUi(R.layout.connection_test)
public class CommCareConnectionDiagActivity extends CommCareActivity<CommCareConnectionDiagActivity> {
	@UiElement(R.id.screen_bulk_image1)
	ImageView banner;
	
	@UiElement(value = R.id.connection_test_prompt, locale="connection.test.prompt")
	TextView connectionPrompt;
	
	@UiElement(value = R.id.run_connection_test, locale="connection.test.run")
	Button btnRunTest;
	
	@UiElement(value = R.id.screen_bulk_form_messages, locale="connection.test.messages")
	TextView txtInteractiveMessages;
	
	//dialog box that displays information about the test, as well as buttons to cancel and send info to cchq
	AlertDialog connectionRunner;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		btnRunTest.setOnClickListener(new OnClickListener() 
		{
			@Override
			public void onClick(View v)
			{
				connectionRunner = newDialog();
				connectionRunner.show();
				ConnectionDiagTask<CommCareConnectionDiagActivity> mConnectionDiagTask = 
				new ConnectionDiagTask<CommCareConnectionDiagActivity>(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform())		
				{	
					@Override
					protected void deliverResult(CommCareConnectionDiagActivity receiver, String result) 
					{
						receiver.connectionRunner.dismiss();
						txtInteractiveMessages.setText(result);
					}

					@Override
					protected void deliverUpdate(CommCareConnectionDiagActivity receiver, String... update) 
					{
						receiver.connectionRunner.setMessage("working");
					}
					
					@Override
					protected void deliverError(CommCareConnectionDiagActivity receiver, Exception e)
					{
						receiver.connectionRunner.setMessage("there's been an error");
					}
				};
				
				mConnectionDiagTask.connect(CommCareConnectionDiagActivity.this);
				mConnectionDiagTask.execute();				
			}
		});
	}
	
	private AlertDialog newDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle((Localization.get("connection.test.run.title")));
		builder.setMessage(Localization.get("connection.test.now.running"))
		.setCancelable(false)
		.setPositiveButton("Send to commCare", new DialogInterface.OnClickListener() 
		{
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.dismiss();
				dialog.cancel();
			}
		});	
		
		AlertDialog dialog = builder.create();
		return dialog;
	}
	
	@Override
	protected Dialog onCreateDialog(int id) 
	{
		if(id == ConnectionDiagTask.CONNECTION_ID) 
		{
			return newDialog();
		}
		return null;
	}
}

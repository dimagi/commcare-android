package org.commcare.dalvik.activities;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
			public void onClick(View v)
			{
				connectionRunner = newDialog();
				connectionRunner.show();
			}
		}
		);
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
			
//			@Override
//			public void onClick(DialogInterface dialog, int which) {
//				// TODO Auto-generated method stub
//				
//			}
//		})
//	}
}

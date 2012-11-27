package org.commcare.dalvik.activities;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.tasks.VerificationTask;
import org.commcare.android.tasks.VerificationTaskListener;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.util.SizeBoundVector;

import android.view.View;
import android.view.View.OnClickListener;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class CommCareVerificationActivity extends Activity implements VerificationTaskListener, OnClickListener {
	
	TextView missingMediaStatus;
	TextView missingMediaPrompt;
	
	public static final String KEY_REQUIRE_REFRESH = "require_referesh";
	
	public static int RESULT_RETRY = 2;
	public static int RESULT_IGNORE = 3;
	
	public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        
        System.out.println("In onCreate");
        
        Bundle extras = getIntent().getExtras();
        setContentView(R.layout.missing_multimedia_layout);
        
        Button retryButton = (Button)findViewById(R.id.RetryButton01);
        
        retryButton.setText("Retry");
        
        retryButton.setOnClickListener(this);
        
        missingMediaPrompt = (TextView)findViewById(R.id.MissingMediaPrompt01);
        missingMediaStatus = (TextView)findViewById(R.id.MissingMediaStatus01);
        
        missingMediaPrompt.setText("Verifying media...");
        setStatusString(0,0);
        verifyResourceInstall();
	}
	
	public void verifyResourceInstall() {
		VerificationTask task = new VerificationTask(this);
		task.setListener(this);
		task.execute((String[])null);
	}

	@Override
	public void onFinished(SizeBoundVector<UnresolvedResourceException> problems) {
		if(problems.size() > 0 ) {
			String message = "Problem with validating resources. Do you want to try to add these reources?";
			
			Hashtable<String, Vector<String>> problemList = new Hashtable<String,Vector<String>>();
			for(Enumeration en = problems.elements() ; en.hasMoreElements() ;) {
				UnresolvedResourceException ure = (UnresolvedResourceException)en.nextElement();
				String res = ure.getResource().getResourceId();
				Vector<String> list;
				if(problemList.containsKey(res)) {
					list = problemList.get(res);
				} else{
					list = new Vector<String>();
				}
				list.addElement(ure.getMessage());
				
				problemList.put(res, list);
			}
			
			for(Enumeration en = problemList.keys(); en.hasMoreElements();) {
				String resource = (String)en.nextElement();
				message += "\nResource: " + resource;
				message += "\n-----------";
				for(String s : problemList.get(resource)) {
					message += "\n" + s;
				}
			}
			if(problems.getAdditional() > 0) {
				message += "\n\n..." + problems.getAdditional() + " more";
			}
			
			missingMediaPrompt.setText(message);
		}
		
		//this.showDialog(DIALOG_VERIFY_PROGRESS);
	}
	
	public void setStatusString(int done, int pending){
		missingMediaStatus.setText("verified " + done + " out of " + pending + " files.");
	}

	@Override
	public void updateVerifyProgress(int done, int pending) {
		setStatusString(done, pending);
		
	}
	
	public void done(boolean requireRefresh) {
		//unlock();
		
		//TODO: We might have gotten here due to being called from the outside, in which
		//case we should manually start up the home activity
		
		if(Intent.ACTION_VIEW.equals(CommCareVerificationActivity.this.getIntent().getAction())) {
			//Call out to CommCare Home
 	       Intent i = new Intent(getApplicationContext(), CommCareHomeActivity.class);
 	       i.putExtra(KEY_REQUIRE_REFRESH, requireRefresh);
 	       startActivity(i);
 	       finish();
 	       
 	       return;
		} else {
			//Good to go
	        Intent i = new Intent(getIntent());
	        i.putExtra(KEY_REQUIRE_REFRESH, requireRefresh);
	        setResult(RESULT_OK, i);
	        finish();
	        return;
		}
	}

	@Override
	public void success() {
		CommCareApplication._().setResourcesValidated(true);
		done(true);
	}

	@Override
	public void failUnknown() {
		missingMediaPrompt.setText("Validation failed for an unknown reason");
		
	}

	@Override
	public void onClick(View v) {
		
		Intent mIntent = new Intent();
		
		switch(v.getId()){
		case R.id.RetryButton01:
			verifyResourceInstall();
			return;
		}
		finish();
	}
}

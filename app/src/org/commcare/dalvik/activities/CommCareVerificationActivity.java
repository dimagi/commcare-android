package org.commcare.dalvik.activities;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.tasks.VerificationTask;
import org.commcare.android.tasks.VerificationTaskListener;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.SizeBoundVector;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class CommCareVerificationActivity extends Activity implements VerificationTaskListener, OnClickListener {
	
	TextView missingMediaPrompt;
	private static final int MENU_UNZIP = Menu.FIRST;
	private ProgressDialog vProgressDialog;
	
	public static final String KEY_REQUIRE_REFRESH = "require_referesh";
	
	Button retryButton;
	
	VerificationTask task;
	
	public static int RESULT_RETRY = 2;
	public static int RESULT_IGNORE = 3;
	
	public static int DIALOG_VERIFY_PROGRESS = 0;
	
	public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        
        Bundle extras = getIntent().getExtras();
        setContentView(R.layout.missing_multimedia_layout);
        
        retryButton = (Button)findViewById(R.id.screen_multimedia_retry);
        
        retryButton.setOnClickListener(this);
        
        missingMediaPrompt = (TextView)findViewById(R.id.MissingMediaPrompt);
        
        fire();
	}
	
	public Object onRetainNonConfigurationInstance() {
		return this;
	}
	
	private void fire() {
        
        CommCareVerificationActivity last = (CommCareVerificationActivity)this.getLastNonConfigurationInstance();
        if(last == null) {
            missingMediaPrompt.setText("Verifying media...");
            retryButton.setText("Retry");
            verifyResourceInstall();
        } else {
        	//For some reason android just isn't recovering our prompt text here, which 
        	//is super obnoxious
        	missingMediaPrompt.setText(last.missingMediaPrompt.getText());
        	if(last.task != null && last.task.getStatus() == Status.RUNNING) {
        		this.task = last.task;
        		last.task.setListener(this);
        	} else {
        		//don't worry about it
        	}
        }
	}
	
	public void verifyResourceInstall() {
		task = new VerificationTask(this);
		task.setListener(this);
		this.showDialog(DIALOG_VERIFY_PROGRESS);
		task.execute((String[])null);
	}

	@Override
	public void onFinished(SizeBoundVector<MissingMediaException> problems) {
		vProgressDialog.dismiss();
		if(problems.size() > 0 ) {
			String message = Localization.get("verification.fail.message");
			
			Hashtable<String, Vector<String>> problemList = new Hashtable<String,Vector<String>>();
			for(Enumeration en = problems.elements() ; en.hasMoreElements() ;) {
				MissingMediaException ure = (MissingMediaException)en.nextElement();
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
				
				message += "\n-----------";
				for(String s : problemList.get(resource)) {
					System.out.println("405 s is: " + s);
					message += "\n" + prettyString(s);
				}
			}
			if(problems.getAdditional() > 0) {
				message += "\n\n..." + problems.getAdditional() + " more";
			}
			
			missingMediaPrompt.setText(message);
		}
		
		//this.showDialog(DIALOG_VERIFY_PROGRESS);
	}
	
	protected Dialog onCreateDialog(int id) {
		if(id == DIALOG_VERIFY_PROGRESS) {
			vProgressDialog = new ProgressDialog(this);
			vProgressDialog.setTitle(Localization.get("verification.title"));
			vProgressDialog.setMessage(Localization.get("verification.checking"));
			return vProgressDialog;
		}
		return null;
	}

	@Override
	public void updateVerifyProgress(int done, int pending) {
		vProgressDialog.setMessage(Localization.get("verification.progress",new String[] {""+done,""+pending}));
		
	}
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	fire();
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
		CommCareApplication._().getCurrentApp().setResourcesValidated(true);
		done(true);
	}

	@Override
	public void failUnknown() {
		missingMediaPrompt.setText("Validation failed for an unknown reason");
		
	}
	
	public String prettyString(String rawString){
		int marker = rawString.indexOf("/sdcard");
		if(marker<0){return rawString;}
		else{return rawString.substring(marker);}
	}

	@Override
	public void onClick(View v) {
		
		Intent mIntent = new Intent();
		
		switch(v.getId()){
		case R.id.screen_multimedia_retry:
			verifyResourceInstall();
			return;
		}
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_UNZIP, 0, "Install Multimedia").setIcon(android.R.drawable.ic_menu_gallery);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_UNZIP:
                Intent i = new Intent(this, MultimediaInflaterActivity.class);
                i.putExtra(MultimediaInflaterActivity.EXTRA_FILE_DESTINATION, CommCareApplication._().getCurrentApp().storageRoot());
                this.startActivityForResult(i, 0);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
}

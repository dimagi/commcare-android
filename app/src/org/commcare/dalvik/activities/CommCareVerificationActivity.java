package org.commcare.dalvik.activities;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.tasks.VerificationTask;
import org.commcare.android.tasks.VerificationTaskListener;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.resources.model.MissingMediaException;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.SizeBoundVector;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * Performs media validation and allows for the installation of missing media
 */
public class CommCareVerificationActivity
        extends CommCareActivity<CommCareVerificationActivity>
        implements VerificationTaskListener, OnClickListener {
    private static final String TAG = CommCareVerificationActivity.class.getSimpleName();

    private TextView missingMediaPrompt;
    private static final int MENU_UNZIP = Menu.FIRST;
    
    private static final String KEY_REQUIRE_REFRESH = "require_referesh";
    
    private Button retryButton;

    private VerificationTask task;

    private static final int DIALOG_VERIFY_PROGRESS = 0;

    /**
     * Return code for launching media inflater (selector).
     */
    private static final int GET_MULTIMEDIA = 0;

    /**
     * When new media is installed, set this so that verification is fired
     * onPostResume.
     */
    private boolean newMediaToValidate = false;

    /**
     * Indicates whether this activity was launched from the AppManagerActivity
     */
    private boolean fromManager;

    public void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.missing_multimedia_layout);
        
        retryButton = (Button)findViewById(R.id.screen_multimedia_retry);
        retryButton.setOnClickListener(this);
                
        this.fromManager = this.getIntent().
        		getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);
        if (fromManager) {
            Button skipButton = (Button)findViewById(R.id.skip_verification_button);
            skipButton.setVisibility(View.VISIBLE);
            skipButton.setOnClickListener(this);
        }
        
        missingMediaPrompt = (TextView)findViewById(R.id.MissingMediaPrompt);
        
        fire();
    }
    
    @Override
    public void onResume() {
        super.onResume();

        // It is possible that the CommCare screen was left off in the VerificationActivity, but
        // then something was done on the Manager screen that means we no longer want to be here --
        // VerificationActivity should be displayed to a user only if we were explicitly sent from
        // the manager, or if the state of installed apps calls for it
        boolean shouldBeHere = fromManager || CommCareApplication._().shouldSeeMMVerification();
        if (!shouldBeHere) {
            finish();
        }
    }
    
    private void fire() {
        CommCareVerificationActivity last = (CommCareVerificationActivity)this.getDestroyedActivityState();
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
                verifyResourceInstall();
                //don't worry about it
            }
        }
    }
    
    private void verifyResourceInstall() {
        task = new VerificationTask();
        task.setListener(this);
        showProgressDialog(DIALOG_VERIFY_PROGRESS);
        task.execute((String[]) null);
    }

    @Override
    public void onFinished(SizeBoundVector<MissingMediaException> problems) {
        dismissProgressDialog();
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
                    message += "\n" + prettyString(s);
                }
            }
            if(problems.getAdditional() > 0) {
                message += "\n\n..." + problems.getAdditional() + " more";
            }
            
            missingMediaPrompt.setText(message);
        }
    }

    @Override
    public void updateVerifyProgress(int done, int pending) {
        updateProgress(Localization.get("verification.progress",new String[] {""+done,""+pending}),
            DIALOG_VERIFY_PROGRESS);
        updateProgressBar(done, pending, DIALOG_VERIFY_PROGRESS);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (newMediaToValidate) {
            newMediaToValidate = false;
            fire();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == GET_MULTIMEDIA && resultCode == Activity.RESULT_OK) {
            // we found some media, so try validating it
            newMediaToValidate = true;
        }
    }

    @Override
    public void success() {
        CommCareApplication._().getCurrentApp().setMMResourcesValidated();
        if(Intent.ACTION_VIEW.equals(CommCareVerificationActivity.this.getIntent().getAction())) {
            //Call out to CommCare Home
            Intent i = new Intent(getApplicationContext(), CommCareHomeActivity.class);
            i.putExtra(KEY_REQUIRE_REFRESH, true);
            startActivity(i);
        } else {
            //Good to go
            Intent i = new Intent(getIntent());
            i.putExtra(KEY_REQUIRE_REFRESH, true);
            setResult(RESULT_OK, i);
        }
        finish();
    }

    @Override
    public void failUnknown() {
        missingMediaPrompt.setText("Validation failed for an unknown reason");
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
                i.putExtra(MultimediaInflaterActivity.EXTRA_FILE_DESTINATION,
                        CommCareApplication._().getCurrentApp().storageRoot());
                this.startActivityForResult(i, GET_MULTIMEDIA);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /*
     * Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId == DIALOG_VERIFY_PROGRESS) {
            CustomProgressDialog dialog = CustomProgressDialog.newInstance
                    (Localization.get("verification.title"), Localization.get("verification.checking"), taskId);
            dialog.addProgressBar();
            return dialog;
        }
        Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                + "any valid possibilities in CommCareVerificationActivity");
        return null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.skip_verification_button:
                Intent i = new Intent(getIntent());
                setResult(RESULT_CANCELED, i);
                finish();
                break;
            case R.id.screen_multimedia_retry:
                verifyResourceInstall();
        }
        
    }

    private String prettyString(String rawString){
        int marker = rawString.indexOf("/sdcard");
        if(marker<0){return rawString;}
        else{return rawString.substring(marker);}
    }

}

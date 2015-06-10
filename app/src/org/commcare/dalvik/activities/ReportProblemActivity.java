package org.commcare.dalvik.activities;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class ReportProblemActivity extends CommCareActivity<ReportProblemActivity> implements OnClickListener {

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_problem);
        Button submitButton = (Button)findViewById(R.id.ReportButton01);
        submitButton.setText(this.localize("problem.report.button"));
        submitButton.setOnClickListener(this);
        ((TextView)findViewById(R.id.ReportPrompt01)).setText(this.localize("problem.report.prompt"));
    }

    /*
     * (non-Javadoc)
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    @Override
    public void onClick(View v) {
        EditText mEdit = (EditText)findViewById(R.id.ReportText01);
        String reportEntry = mEdit.getText().toString();
        Logger.log(AndroidLogger.USER_REPORTED_PROBLEM, reportEntry);
        setResult(RESULT_OK);
        sendReportEmail(reportEntry);
        finish();
    }
    
    public String buildMessage(String userInput){
        
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        
        String username;
        try {
            username = CommCareApplication._().getSession().getLoggedInUser().getUsername();
        } catch (SessionUnavailableException e) {
            username = "unknown_username";
        }
        String version = CommCareApplication._().getCurrentVersionString();
        String domain = prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, "not found");
        String postURL = prefs.getString("PostURL", null);

        return "Problem reported via CommCareODK. " +
                "\n User: " + username +
                "\n Domain: " + domain +
                "\n PostURL: " + postURL +
                "\n CCODK version: " + version +
                "\n Device Model: " + Build.MODEL +
                "\n Manufacturer: " + Build.MANUFACTURER +
                "\n Android Version: " + Build.VERSION.RELEASE +
                "\n Message: " + userInput;
    }
    
    public void sendReportEmail(String report){
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        i.putExtra(Intent.EXTRA_EMAIL  , new String[]{"commcarehq-support@dimagi.com"});
        i.putExtra(Intent.EXTRA_TEXT, this.buildMessage(report));
        i.putExtra(Intent.EXTRA_SUBJECT   , "Mobile Error Report");

        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(ReportProblemActivity.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
        }
    }

}

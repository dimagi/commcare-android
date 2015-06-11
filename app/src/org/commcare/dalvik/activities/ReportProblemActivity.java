package org.commcare.dalvik.activities;

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

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;

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

    public static String getDomain(){
        try {
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            return prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, "not found");
        } catch(NullPointerException e){
            return "Domain not set.";
        }
    }

    public static String getPostURL(){
        try{
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            return prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, "not found");
        } catch(NullPointerException e){
            return "PostURL not set.";
        }
    }

    public static String getUser(){
        try{
            return CommCareApplication._().getSession().getLoggedInUser().getUsername();
        } catch(SessionUnavailableException e){
            return "User not logged in.";
        }
    }

    public static String getVersion(){
        try {
            return CommCareApplication._().getCurrentVersionString();
        } catch(NullPointerException e){
            return "Version not set.";
        }
    }

    public static String buildMessage(String userInput){

        String domain = ReportProblemActivity.getDomain();
        String postURL = ReportProblemActivity.getPostURL();
        String version= ReportProblemActivity.getVersion();
        String username = ReportProblemActivity.getUser();

        String message = "Problem reported via CommCareODK. " +
                "\n User: " + username +
                "\n Domain: " + domain +
                "\n PostURL: " + postURL +
                "\n CCODK version: " + version +
                "\n Device Model: " + Build.MODEL +
                "\n Manufacturer: " + Build.MANUFACTURER +
                "\n Android Version: " + Build.VERSION.RELEASE +
                "\n Message: " + userInput;
        return message;
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
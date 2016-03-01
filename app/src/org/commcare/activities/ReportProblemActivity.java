package org.commcare.activities;

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

import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.logging.AndroidLogger;
import org.commcare.network.HttpRequestGenerator;
import org.javarosa.core.services.Logger;

public class ReportProblemActivity extends SessionAwareCommCareActivity<ReportProblemActivity> implements OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_problem);
        Button submitButton = (Button)findViewById(R.id.ReportButton01);
        submitButton.setText(this.localize("problem.report.button"));
        submitButton.setOnClickListener(this);
        ((TextView)findViewById(R.id.ReportPrompt01)).setText(this.localize("problem.report.prompt"));
    }

    @Override
    public void onClick(View v) {
        EditText mEdit = (EditText)findViewById(R.id.ReportText01);
        String reportEntry = mEdit.getText().toString();
        Logger.log(AndroidLogger.USER_REPORTED_PROBLEM, reportEntry);
        setResult(RESULT_OK);
        sendReportEmail(reportEntry);
        finish();
    }

    /*
     * Helper methods for ACRA and user reporting. Catch broad exception so we never crash
     * when trying to file a bug.
     */

    public static String getDomain() {
        try {
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            return prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, "not found");
        } catch (Exception e) {
            return "Domain not set.";
        }
    }

    public static String getPostURL() {
        try {
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            return prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, "not found");
        } catch (Exception e) {
            return "PostURL not set.";
        }
    }

    public static String getUser() {
        try {
            return CommCareApplication._().getSession().getLoggedInUser().getUsername();
        } catch (Exception e) {
            return "User not logged in.";
        }
    }

    public static String getVersion() {
        try {
            return CommCareApplication._().getCurrentVersionString();
        } catch (Exception e) {
            return "Version not set.";
        }
    }

    private static String buildMessage(String userInput) {
        String domain = ReportProblemActivity.getDomain();
        String postURL = ReportProblemActivity.getPostURL();
        String version = ReportProblemActivity.getVersion();
        String username = ReportProblemActivity.getUser();

        return "Problem reported via CommCare. " +
                "\n User: " + username +
                "\n Domain: " + domain +
                "\n PostURL: " + postURL +
                "\n CCDroid version: " + version +
                "\n Device Model: " + Build.MODEL +
                "\n Manufacturer: " + Build.MANUFACTURER +
                "\n Android Version: " + Build.VERSION.RELEASE +
                "\n Message: " + userInput;
    }

    private void sendReportEmail(String report) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{"commcarehq-support@dimagi.com"});
        i.putExtra(Intent.EXTRA_TEXT, ReportProblemActivity.buildMessage(report));
        i.putExtra(Intent.EXTRA_SUBJECT, "Mobile Error Report");

        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(ReportProblemActivity.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
        }
    }
}

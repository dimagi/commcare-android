package org.commcare.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.logging.ReportingUtils;
import org.commcare.dalvik.R;
import org.commcare.logging.AndroidLogger;
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

    private static String buildMessage(String userInput) {
        String domain = ReportingUtils.getDomain();
        String postURL = ReportingUtils.getPostURL();
        String version = ReportingUtils.getVersion();
        String username = ReportingUtils.getUser();

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

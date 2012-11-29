package org.commcare.dalvik.activities;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.dalvik.R;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class ReportProblemActivity extends Activity implements OnClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_problem);
        Button submitButton = (Button)findViewById(R.id.ReportButton01);
        submitButton.setText(Localization.get("problem.report.button"));
        submitButton.setOnClickListener(this);
        ((TextView)findViewById(R.id.ReportPrompt01)).setText(Localization.get("problem.report.prompt"));
    }

	@Override
	public void onClick(View v) {
		EditText mEdit = (EditText)findViewById(R.id.ReportText01);
		String reportEntry = mEdit.getText().toString();
		Logger.log(AndroidLogger.USER_REPORTED_PROBLEM, reportEntry);
		setResult(RESULT_OK);
		finish();
	}

}

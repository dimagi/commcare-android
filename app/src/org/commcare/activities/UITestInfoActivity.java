package org.commcare.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import org.commcare.dalvik.R;

// Used for ui testing components which don't have UI components
public class UITestInfoActivity extends AppCompatActivity {

    public static final String LOG_SUBMISSION_RESULT_PREF = "log_submission_result";
    private static final String EXTRA_INFO_TYPE = "info_type";
    private TextView infoTv;

    private final String INFO_TYPE_LOG_SUBMISSION = "log_submission";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uitest_info);
        setTitle("UI Test Info");
        infoTv = (TextView)findViewById(R.id.infoTv);
        loadInfo();
    }

    private void loadInfo() {
        String infoType = getIntent().getStringExtra(EXTRA_INFO_TYPE);
        switch (infoType) {
            case INFO_TYPE_LOG_SUBMISSION:
                loadLogSubmissionInfo();
                break;
            default:
                infoTv.setText("Invalid Info Type");
        }
    }

    private void loadLogSubmissionInfo() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean result = sharedPreferences.getBoolean(LOG_SUBMISSION_RESULT_PREF, false);
        infoTv.setText(result ? "Logs successfully submitted" : "Error submitting logs");
    }
}

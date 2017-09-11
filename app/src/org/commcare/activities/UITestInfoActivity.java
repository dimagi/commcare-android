package org.commcare.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;

// Used for ui testing components which don't have UI components
public class UITestInfoActivity extends FragmentActivity {

    public static final String LOG_SUBMISSION_RESULT_PREF = "log_submission_result";
    private static final String EXTRA_INFO_TYPE = "info_type";
    private TextView infoTv;

    private final int INFO_TYPE_LOG_SUBMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uitest_info);
        setTitle("UI Test Info");
        infoTv = findViewById(R.id.infoTv);
        loadInfo();
    }

    private void loadInfo() {
        int infoType = getIntent().getIntExtra(EXTRA_INFO_TYPE, 0);
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

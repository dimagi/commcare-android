package org.commcare.activities;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.tasks.LogSubmissionTask;

// Used for ui testing components which don't have UI components
public class UITestInfoActivity extends FragmentActivity {

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
        switch (infoType){
            case INFO_TYPE_LOG_SUBMISSION:
                loadLogSubmissionInfo();
                break;
            default:
                infoTv.setText("Invalid Info Type");
        }
    }

    private void loadLogSubmissionInfo() {
        SqlStorage<DeviceReportRecord> storage =
                CommCareApplication.instance().getUserStorage(DeviceReportRecord.class);
        LogSubmissionTask.serializeLogs(storage);
        infoTv.setText(storage.getNumRecords() + " logs to submit");
    }
}

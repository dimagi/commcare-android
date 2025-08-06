package org.commcare.interfaces;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.models.connect.ConnectLoginJobListModel;

public interface OnJobSelectionClick{
    void onClick(ConnectJobRecord job, boolean isLearning, String appId,
                 ConnectLoginJobListModel.JobListEntryType jobType);
}

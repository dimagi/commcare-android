package org.commcare.interfaces;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.models.connect.ConnectLoginJobListModel;

public interface OnJobSelectionClick {

    enum Action {
        RESUME,
        VIEW_INFO
    }

    void onClick(
            ConnectJobRecord job,
            boolean isLearning,
            String appId,
            ConnectLoginJobListModel.JobListEntryType jobType,
            Action action
    );
}

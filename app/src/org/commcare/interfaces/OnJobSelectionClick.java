package org.commcare.interfaces;

import org.commcare.android.database.connect.models.ConnectJobRecord;

public interface OnJobSelectionClick{
    void onClick(ConnectJobRecord job, boolean isLearning, String appId, String jobType);
}

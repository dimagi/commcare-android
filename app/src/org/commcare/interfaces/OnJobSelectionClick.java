package org.commcare.interfaces;

import org.commcare.android.database.connect.models.ConnectJobRecord;

public interface OnJobSelectionClick{
    void onClick(ConnectJobRecord job, String appId, String jobType);
}

package org.commcare.connect;

import android.view.View;

import org.commcare.android.database.connect.models.ConnectJobRecord;

public interface ConnectJobListLauncher {
    void launchApp(ConnectJobRecord job, View view,boolean isAvailable);
}

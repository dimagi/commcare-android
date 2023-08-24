package org.commcare.activities.connect;

import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;

import javax.annotation.Nullable;

public class ConnectActivity extends CommCareActivity<ConnectActivity> {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_connect);

        setTitle(getString(R.string.connect_title));
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }
}

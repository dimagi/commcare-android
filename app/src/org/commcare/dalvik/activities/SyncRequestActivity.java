package org.commcare.dalvik.activities;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.android.framework.CommCareActivity;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SyncRequestActivity extends CommCareActivity<SyncRequestActivity> {
    private static final int SELECT_QUERY_RESULT = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // prompt user for input
        // submit query
        // forward to entity list
        // on selection perform post
        // trigger sync
        // go to next session stack frame
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            if requestCode
        } else {
        }
    }

    private void launchEntitySelctionActivity() {
        Intent i = new Intent(this, EntitySelectActivity.class);
        startActivityForResult(i, SELECT_QUERY_RESULT);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}

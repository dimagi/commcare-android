package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class QueryRequestActivity extends CommCareActivity<QueryRequestActivity> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // prompt user for input
        // submit query
        // forward to entity list
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
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

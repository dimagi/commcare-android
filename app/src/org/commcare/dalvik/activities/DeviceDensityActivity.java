package org.commcare.dalvik.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;

/**
 * Activity to be used as a callout for computing the screen density of a user's device
 */
public class DeviceDensityActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int deviceDensity = displayMetrics.densityDpi;
        Intent intent = new Intent();
        Bundle responses = new Bundle();
        responses.putString("density_value", "" + deviceDensity);
        intent.putExtra("odk_intent_bundle", responses);
        setResult(RESULT_OK, intent);
        finish();
    }

}

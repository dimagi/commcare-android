package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.commcare.dalvik.R;

/**
 * Created by amstone326 on 6/8/16.
 */
public class MultipleAppsLimitWarningActivity extends CommCareActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.multiple_apps_limit_view);
        boolean installAttemptCameFromAppManager = getIntent().getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);
        Button toManagerButton = (Button)findViewById(R.id.back_to_manager_button);

        if (installAttemptCameFromAppManager) {
            toManagerButton.setText("Back to App Manager");
            toManagerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setResult(RESULT_OK);
                    finish();
                }
            });
        } else {
            toManagerButton.setText("Go to App Manager");
            toManagerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(MultipleAppsLimitWarningActivity.this, AppManagerActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            });
        }

    }

}

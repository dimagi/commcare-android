package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.commcare.dalvik.R;

/**
 * Activity that is shown when a user tries to install more than 2 apps at a time, without having
 * either superuser privileges or a multiple apps seat enabled on the device.
 *
 * @author Aliza Stone (astone@dimagi.com), created 6/9/16.
 */
public class MultipleAppsLimitWarningActivity extends CommCareActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.multiple_apps_limit_view);
        boolean installAttemptCameFromAppManager = getIntent()
                .getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);
        setupUI(installAttemptCameFromAppManager);
    }

    private void setupUI(final boolean installAttemptCameFromManager) {
        Button toManagerButton = (Button)findViewById(R.id.back_to_manager_button);

        if (installAttemptCameFromManager) {
            toManagerButton.setText(R.string.back_to_manager);
        } else {
            toManagerButton.setText(R.string.go_to_manager);
        }

        toManagerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               onManagerButtonClicked(installAttemptCameFromManager);
            }
        });
    }

    private void onManagerButtonClicked(boolean installAttemptCameFromManager) {
        if (installAttemptCameFromManager) {
            setResult(RESULT_OK);
            finish();
        } else {
            Intent i = new Intent(MultipleAppsLimitWarningActivity.this, AppManagerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

}

package org.commcare.dalvik.activities;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import org.commcare.android.framework.UiElement;
import org.commcare.dalvik.R;

import static java.lang.Thread.sleep;

/**
 * Allow user to manage app upgrading:
 *  - Check and downloading new latest upgrade
 *  - Stop an upgrade download
 *  - Apply a downloaded upgrade
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpgradeActivity extends Activity {
    private static final String TAG = UpgradeActivity.class.getSimpleName();

    private ProgressBar mProgress;
    private int mProgressStatus = 0;

    private Handler mHandler = new Handler();

    @UiElement(R.id.check_for_upgrade_button)
    Button checkUpgradeButton;

    @UiElement(R.id.stop_upgrade_download_button)
    Button stopUpgradeButton;

    @UiElement(R.id.install_upgrade_button)
    Button installUpgradeButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setup UI
        setContentView(R.layout.upgrade_activity);
        mProgress = (ProgressBar)findViewById(R.id.upgrade_progress_bar);
        setupButtonListeners();

        // attempt to attach to auto-update task

        // update UI based on current state
        setupButtonState();

        // Start lengthy operation in a background thread
        new Thread(new Runnable() {
            public void run() {
                while (mProgressStatus < 100) {
                    mProgressStatus = doWork();

                    // Update the progress bar
                    mHandler.post(new Runnable() {
                        public void run() {
                            mProgress.setProgress(mProgressStatus);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public int doWork() {
        try {
            sleep(1000);
        } catch (Exception e) {
        }
        return mProgressStatus + 1;
    }

    private void setupButtonListeners() {
        checkUpgradeButton = (Button)findViewById(R.id.check_for_upgrade_button);
        checkUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        stopUpgradeButton = (Button)findViewById(R.id.stop_upgrade_download_button);
        stopUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        installUpgradeButton = (Button)findViewById(R.id.install_upgrade_button);
        installUpgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
    }

    private void setupButtonState() {
    }
}

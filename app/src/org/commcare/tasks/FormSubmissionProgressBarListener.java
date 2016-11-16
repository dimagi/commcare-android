package org.commcare.tasks;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.os.Build;
import android.view.View;
import android.widget.ProgressBar;

import org.commcare.activities.SyncCapableCommCareActivity;
import org.commcare.dalvik.R;

/**
 * Created by amstone326 on 11/16/16.
 */
public class FormSubmissionProgressBarListener implements DataSubmissionListener {

    private static final long MIN_PROGRESS_BAR_DURATION_MS = 1500;

    private long sizeOfCurrentItem;
    private long startTime;
    private ProgressBar submissionProgressBar;
    private SyncCapableCommCareActivity containingActivity;

    public FormSubmissionProgressBarListener(SyncCapableCommCareActivity activityContainingProgressBar) {
        this.containingActivity = activityContainingProgressBar;
    }

    @Override
    public void beginSubmissionProcess(int totalItems) {
        startTime = System.currentTimeMillis();
        containingActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                submissionProgressBar =
                        (ProgressBar)containingActivity.findViewById(R.id.submission_progress_bar);
                submissionProgressBar.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void startSubmission(int itemNumber, long sizeOfItem) {
        sizeOfCurrentItem = sizeOfItem;
        containingActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                submissionProgressBar.setMax(100);
                submissionProgressBar.setProgress(0);
            }
        });
    }

    @Override
    public void notifyProgress(int itemNumber, final long progress) {
        containingActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                submissionProgressBar.setProgress(getProgressToReport(progress));
            }
        });
    }

    @Override
    public void endSubmissionProcess() {
        containingActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (submissionProgressBar.getProgress() != 100) {
                    finishAnimatingProgressBar();
                } else {
                    submissionProgressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private int getProgressToReport(long progressForCurrentItem) {
        final int actualProgressPercent =
                (int)Math.floor((progressForCurrentItem * 1.0 / sizeOfCurrentItem) * 100);

        long timeElapsed = System.currentTimeMillis() - startTime;
        final int maxAllowedProgressByTime =
                (int)Math.floor((timeElapsed * 1.0 / MIN_PROGRESS_BAR_DURATION_MS) * 100);

        return Math.min(actualProgressPercent, maxAllowedProgressByTime);
    }

    private void finishAnimatingProgressBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ObjectAnimator animation = ObjectAnimator.ofInt(submissionProgressBar, "progress",
                    submissionProgressBar.getProgress(), 100);
            long timeRemaining =
                    MIN_PROGRESS_BAR_DURATION_MS - (System.currentTimeMillis() - startTime);
            animation.setDuration(timeRemaining);
            animation.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    containingActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            submissionProgressBar.setVisibility(View.GONE);
                        }
                    });
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            animation.start();
        } else {
            submissionProgressBar.setProgress(100);
            submissionProgressBar.setVisibility(View.GONE);
        }
    }

}

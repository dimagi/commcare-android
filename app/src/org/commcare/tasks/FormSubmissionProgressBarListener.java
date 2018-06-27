package org.commcare.tasks;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;

import org.commcare.activities.SyncCapableCommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.interfaces.UiLoadedListener;

/**
 * A DataSubmissionListener that updates a progress bar in the given activity
 *
 * @author Aliza Stone
 */
public class FormSubmissionProgressBarListener implements DataSubmissionListener, UiLoadedListener {

    private static final long MIN_PROGRESS_BAR_DURATION_PER_ITEM = 1000;
    private static final long MAX_TOTAL_PROGRESS_BAR_DURATION = 5000;

    private int totalItems;
    private int maxProgress;
    private int currentProgress;
    private long sizeOfCurrentItem;
    private long startTime;
    private ProgressBar submissionProgressBar;
    private SyncCapableCommCareActivity containingActivity;

    public FormSubmissionProgressBarListener(SyncCapableCommCareActivity activityContainingProgressBar) {
        this.containingActivity = activityContainingProgressBar;
    }

    /**
     * Called when the associated ProcessAndSendTask's connecting activity changes
     */
    public void attachToNewActivity(SyncCapableCommCareActivity newActivity) {
        if (newActivity != containingActivity) {
            this.containingActivity = newActivity;
            showProgressBarInActivity(this.currentProgress);
        }
    }

    @Override
    public void beginSubmissionProcess(int totalItems) {
        this.totalItems = totalItems;
        // Give each item 100 units of progress to use
        this.maxProgress = totalItems * 100;
        this.startTime = System.currentTimeMillis();
        showProgressBarInActivity(0);
    }

    private void showProgressBarInActivity(final int progressToSet) {
        this.containingActivity.runOnUiThread(() -> {
            submissionProgressBar =
                    containingActivity.findViewById(R.id.submission_progress_bar);
            if (submissionProgressBar == null) {
                // Means that the activity has not finished loading its UI yet, so we have to wait
                containingActivity.setUiLoadedListener(FormSubmissionProgressBarListener.this);
            } else {
                submissionProgressBar.setVisibility(View.VISIBLE);
                submissionProgressBar.setMax(maxProgress);
                submissionProgressBar.setProgress(progressToSet);
            }
        });
    }

    @Override
    public void startSubmission(int itemNumber, long sizeOfItem) {
        sizeOfCurrentItem = sizeOfItem;
    }

    @Override
    public void notifyProgress(final int itemNumber, final long progress) {
        containingActivity.runOnUiThread(() -> {
            int nextProgress = getProgressToReport(itemNumber, progress);
            if (nextProgress > FormSubmissionProgressBarListener.this.currentProgress) {
                submissionProgressBar.setProgress(nextProgress);
                FormSubmissionProgressBarListener.this.currentProgress = nextProgress;
            }
        });
    }

    private int getProgressToReport(int itemNumber, long progressForCurrentItem) {
        int progressPercentForPriorItems = 100 * itemNumber;
        int progressPercentForCurrentItem =
                (int)Math.floor((progressForCurrentItem * 1.0 / sizeOfCurrentItem) * 100);
        int actualProgressPercent = progressPercentForPriorItems + progressPercentForCurrentItem;

        long timeElapsed = System.currentTimeMillis() - startTime;
        final int maxAllowedProgressByTime =
                (int)Math.floor((timeElapsed * 1.0 / getIdealDuration()) * maxProgress);

        return Math.min(actualProgressPercent, maxAllowedProgressByTime);
    }

    @Override
    public void endSubmissionProcess(final boolean success) {
        containingActivity.runOnUiThread(() -> {
            if (success && submissionProgressBar.getProgress() < maxProgress) {
                finishAnimatingProgressBar();
            } else {
                submissionProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private void finishAnimatingProgressBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ObjectAnimator animation = ObjectAnimator.ofInt(submissionProgressBar, "progress",
                    submissionProgressBar.getProgress(), maxProgress);
            animation.setDuration(getFinishAnimationDuration());
            animation.setInterpolator(new DecelerateInterpolator());
            animation.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    containingActivity.runOnUiThread(() -> submissionProgressBar.setVisibility(View.GONE));
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
            submissionProgressBar.setProgress(maxProgress);
            submissionProgressBar.setVisibility(View.GONE);
        }
    }

    private int getFinishAnimationDuration() {
        int progressRemaining = maxProgress - submissionProgressBar.getProgress();
        double proportionRemaining = progressRemaining * 1.0 / maxProgress;
        return (int)Math.floor(getIdealDuration() * proportionRemaining);
    }

    private long getIdealDuration() {
        return Math.min(MIN_PROGRESS_BAR_DURATION_PER_ITEM * totalItems, MAX_TOTAL_PROGRESS_BAR_DURATION);
    }

    @Override
    public void onUiLoaded() {
        showProgressBarInActivity(this.currentProgress);
        // we only want to trigger this once
        this.containingActivity.removeUiLoadedListener();
    }
}

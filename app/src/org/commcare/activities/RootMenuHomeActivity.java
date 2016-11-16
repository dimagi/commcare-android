package org.commcare.activities;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;

import org.commcare.activities.components.MenuList;
import org.commcare.dalvik.R;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Menu;
import org.commcare.tasks.DataSubmissionListener;

/**
 * A version of the CommCare home screen that uses the UI of the root module menu
 * displayed in grid view, and makes all home screen actions available via a
 * navigation drawer (instead of via the usual home screen buttons and options menu)
 *
 * @author Aliza Stone
 */
public class RootMenuHomeActivity extends HomeScreenBaseActivity<RootMenuHomeActivity> {

    private static final String KEY_DRAWER_WAS_OPEN = "drawer-open-before-rotation";
    private static final long MIN_PROGRESS_BAR_DURATION_MS = 1500;

    private HomeNavDrawerController navDrawerController;
    private boolean reopenDrawerInOnResume;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        if (menuId == null) {
            menuId = Menu.ROOT_MENU_ID;
        }
        MenuList.setupMenuViewInActivity(this, menuId, true, true);
        navDrawerController = new HomeNavDrawerController(this);
        if (usingNavDrawer()) {
            navDrawerController.setupNavDrawer();
            if (savedInstanceState != null && savedInstanceState.getBoolean(KEY_DRAWER_WAS_OPEN)) {
                // Necessary because opening the drawer here does not work for some unknown reason
                reopenDrawerInOnResume = true;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (reopenDrawerInOnResume) {
            navDrawerController.openDrawer();
            reopenDrawerInOnResume = false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (usingNavDrawer() && item.getItemId() == android.R.id.home) {
            if (navDrawerController.isDrawerOpen()) {
                navDrawerController.closeDrawer();
            } else {
                navDrawerController.openDrawer();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_DRAWER_WAS_OPEN, navDrawerController.isDrawerOpen());
    }

    private boolean usingNavDrawer() {
        // It's possible that this activity is being used as the home screen without having this flag
        // set explicitly (if this is a consumer app), in which case we don't want to show user actions
        return DeveloperPreferences.useRootModuleMenuAsHomeScreen() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    @Override
    public boolean shouldShowSyncItemInActionBar() {
        // It's possible that this activity is being used as the home screen without having this flag
        // set explicitly (if this is a consumer app), in which case we don't want to show user actions
        return DeveloperPreferences.useRootModuleMenuAsHomeScreen();
    }

    @Override
    public void reportSyncResult(String message, boolean success) {
        super.reportSyncResult(message, success);
        if (usingNavDrawer()) {
            navDrawerController.refreshItems();
        }
    }

    @Override
    public void refreshUI() {
        // empty intentionally
    }

    @Override
    public DataSubmissionListener getListenerForSubmissionProgressBar() {

        return new DataSubmissionListener() {

            long sizeOfCurrentItem;
            long startTime;
            ProgressBar submissionProgressBar;

            @Override
            public void beginSubmissionProcess(int totalItems) {
                startTime = System.currentTimeMillis();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        submissionProgressBar =
                                (ProgressBar)findViewById(R.id.submission_progress_bar);
                        submissionProgressBar.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void startSubmission(int itemNumber, long sizeOfItem) {
                sizeOfCurrentItem = sizeOfItem;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        submissionProgressBar.setMax(100);
                        submissionProgressBar.setProgress(0);
                    }
                });
            }

            @Override
            public void notifyProgress(int itemNumber, final long progress) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        submissionProgressBar.setProgress(getProgressToReport(progress));
                    }
                });
            }

            @Override
            public void endSubmissionProcess() {
                runOnUiThread(new Runnable() {
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
                            runOnUiThread(new Runnable() {
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
        };
    }

}

package org.commcare.activities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.Spannable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.SearchView;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.ACase;
import org.commcare.fragments.BreadcrumbBarFragment;
import org.commcare.fragments.ContainerFragment;
import org.commcare.fragments.TaskConnectorFragment;
import org.commcare.interfaces.WithUIController;
import org.commcare.logging.AndroidLogger;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.utils.MarkupUtil;
import org.commcare.utils.SessionStateUninitException;
import org.commcare.views.ManagedUiFramework;
import org.commcare.views.dialogs.AlertDialogFactory;
import org.commcare.views.dialogs.AlertDialogFragment;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.DialogController;
import org.commcare.views.media.AudioController;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

/**
 * Base class for CommCareActivities to simplify
 * common localization and workflow tasks
 *
 * @author ctsims
 */
public abstract class CommCareActivity<R> extends FragmentActivity
        implements CommCareTaskConnector<R>, DialogController, OnGestureListener {

    private static String TAG = CommCareActivity.class.getSimpleName();

    private static final String KEY_PROGRESS_DIALOG_FRAG = "progress-dialog-fragment";
    private static final String KEY_ALERT_DIALOG_FRAG = "alert-dialog-fragment";

    int invalidTaskIdMessageThrown = -2;

    TaskConnectorFragment<R> stateHolder;

    // Fields for implementing task transitions for CommCareTaskConnector
    private boolean inTaskTransition;

    /**
     * Used to indicate that the (progress) dialog associated with a task
     * should be dismissed because the task has completed or been canceled.
     */
    private boolean dismissLastDialogAfterTransition = true;

    protected AlertDialogFragment alertDialogToShowOnResume;

    private GestureDetector mGestureDetector;

    private static final String KEY_LAST_QUERY_STRING = "LAST_QUERY_STRING";
    protected String lastQueryString;

    /**
     * Activity has been put in the background. Flag prevents dialogs
     * from being shown while activity isn't active.
     */
    private boolean areFragmentsPaused = true;

    /**
     * Mark when task tried to show progress dialog before fragments have resumed,
     * so that the dialog can be shown when fragments have fully resumed.
     */
    private boolean triedBlockingWhilePaused;
    private boolean triedDismissingWhilePaused;

    /**
     * Store the id of a task progress dialog so it can be disabled/enabled
     * on activity pause/resume.
     */
    private int dialogId = -1;
    private ContainerFragment<Bundle> managedUiState;
    private boolean isMainScreenBlocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = this.getSupportFragmentManager();

        stateHolder = (TaskConnectorFragment<R>) fm.findFragmentByTag("state");

        // stateHolder and its previous state aren't null if the activity is
        // being created due to an orientation change.
        if (stateHolder == null) {
            stateHolder = new TaskConnectorFragment<>();
            fm.beginTransaction().add(stateHolder, "state").commit();
            // entering new activity, not just rotating one, so release old
            // media
            AudioController.INSTANCE.releaseCurrentMediaEntity();
        }

        // For activities using a uiController, this must be called before persistManagedUiState()
        if (usesUIController()) {
            ((WithUIController)this).initUIController();
        }

        persistManagedUiState(fm);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().setDisplayShowCustomEnabled(true);

            // Add breadcrumb bar
            BreadcrumbBarFragment bar = (BreadcrumbBarFragment) fm.findFragmentByTag("breadcrumbs");

            // If the state holder is null, create a new one for this activity
            if (bar == null) {
                bar = new BreadcrumbBarFragment();
                fm.beginTransaction().add(bar, "breadcrumbs").commit();
            }
        }

        mGestureDetector = new GestureDetector(this, this);
    }

    private void persistManagedUiState(FragmentManager fm) {
        if (isManagedUiActivity()) {
            managedUiState = (ContainerFragment)fm.findFragmentByTag("ui-state");

            if (managedUiState == null) {
                managedUiState = new ContainerFragment<>();
                fm.beginTransaction().add(managedUiState, "ui-state").commit();
                loadUiElementState(null);
            } else {
                loadUiElementState(managedUiState.getData());
            }
        }
    }

    private void loadUiElementState(Bundle savedInstanceState) {
        ManagedUiFramework.setContentView(this);

        if (savedInstanceState != null) {
            ManagedUiFramework.restoreUiElements(this, savedInstanceState);
        } else {
            ManagedUiFramework.loadUiElements(this);
        }
    }

    /**
     * Call this method from an implementing activity to request a new event trigger for any time
     * the available space for the core content view changes significantly, for instance when the
     * soft keyboard is displayed or hidden.
     *
     * This method will also be reliably triggered upon the end of the first layout pass, so it
     * can be used to do the initial setup for adaptive layouts as well as their updates.
     *
     * After this is called, major layout size changes will be triggered in the onMajorLayoutChange
     * method.
     */
    protected void requestMajorLayoutUpdates() {
        final View decorView = getWindow().getDecorView();
        decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            int mPreviousDecorViewFrameHeight = 0;

            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                //r will be populated with the coordinates of your view that are visible after the
                //recent change.
                decorView.getWindowVisibleDisplayFrame(r);

                int mainContentHeight = r.height();

                int previousMeasurementDifference = Math.abs(mainContentHeight - mPreviousDecorViewFrameHeight);

                if (previousMeasurementDifference > 100) {
                    onMajorLayoutChange(r);
                }
                mPreviousDecorViewFrameHeight = mainContentHeight;
            }
        });
    }

    /**
     * This method is called when the root view size available to the activity has changed
     * significantly. It is the appropriate place to trigger adaptive layout behaviors.
     *
     * Note for performance that changes to declarative view properties here will trigger another
     * layout pass.
     *
     * This callback is only triggered if the parent view has called requestMajorLayoutUpdates
     *
     * @param newRootViewDimensions The dimensions of the new root screen view that is available
     *                              to the activity.
     */
    protected void onMajorLayoutChange(Rect newRootViewDimensions) {

   }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected boolean isTopNavEnabled() {
        return false;
    }

    /**
     * If a message for the user has been set in CommCareApplication, show it and then clear it
     */
    private void showPendingUserMessage() {
        String[] messageAndTitle = CommCareApplication._().getPendingUserMessage();
        if (messageAndTitle != null) {
            showAlertDialog(AlertDialogFactory.getBasicAlertFactory(
                    this, messageAndTitle[1], messageAndTitle[0], null));
            CommCareApplication._().clearPendingUserMessage();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            // In honeycomb and above the fragment takes care of this
            this.setTitle(getTitle(this, getActivityTitle()));
        }

        AudioController.INSTANCE.playPreviousAudio();
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        areFragmentsPaused = false;

        syncTaskBlockingWithDialogFragment();

        showPendingAlertDialog();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isManagedUiActivity()) {
            managedUiState.setData(ManagedUiFramework.saveUiStateToBundle(this));
        }

        areFragmentsPaused = true;
        AudioController.INSTANCE.systemInducedPause();
    }

    @Override
    public <A, B, C> void connectTask(CommCareTask<A, B, C, R> task) {
        stateHolder.connectTask(task, this);

        // If we've left an old dialog showing during the task transition and it was from the same
        // task as the one that is starting, we want to just leave it up for the next task too
        CustomProgressDialog currDialog = getCurrentProgressDialog();
        if (currDialog != null && currDialog.getTaskId() == task.getTaskId()) {
            dismissLastDialogAfterTransition = false;
        }
    }

    /**
     * @return wakelock level for an activity with a running task attached to
     * it; defaults to not using wakelocks.
     */
    public int getWakeLockLevel() {
        return CommCareTask.DONT_WAKELOCK;
    }

    /**
     * Sync progress dialog fragment with any task state changes that may have
     * occurred while the activity was paused.
     */
    private void syncTaskBlockingWithDialogFragment() {
        if (triedDismissingWhilePaused) {
            triedDismissingWhilePaused = false;
            dismissProgressDialog();
        } else if (triedBlockingWhilePaused) {
            triedBlockingWhilePaused = false;
            showNewProgressDialog();
        }
    }

    @Override
    public void startBlockingForTask(int id) {
        dialogId = id;

        if (areFragmentsPaused) {
            // post-pone dialog transactions until after fragments have fully resumed.
            triedBlockingWhilePaused = true;
        } else {
            showNewProgressDialog();
        }
    }

    private void showNewProgressDialog() {
        // Only show a new dialog if we chose to dismiss the old one; If
        // dismissLastDialogAfterTransition is false, that means we left the last dialog up and do
        // not need to create a new one
        if (dismissLastDialogAfterTransition) {
            dismissProgressDialog();
            showProgressDialog(dialogId);
        }
    }

    @Override
    public void stopBlockingForTask(int id) {
        dialogId = -1;

        if (id >= 0) {
            if (inTaskTransition) {
                dismissLastDialogAfterTransition = true;
            } else {
                dismissProgressDialog();
            }
        }

        stateHolder.releaseWakeLock();
    }

    @Override
    public R getReceiver() {
        return (R) this;
    }

    @Override
    public void startTaskTransition() {
        inTaskTransition = true;
    }

    @Override
    public void stopTaskTransition() {
        inTaskTransition = false;
        if (dismissLastDialogAfterTransition) {
            dismissProgressDialog();
            // Re-set shouldDismissDialog to true after this transition cycle is over
            dismissLastDialogAfterTransition = true;
        }
    }

    /**
     * Display exception details as a pop-up to the user.
     *
     * @param e Exception to handle
     */
    public void displayException(Exception e) {
        String title =  Localization.get("notification.case.predicate.title");
        String message = Localization.get("notification.case.predicate.action", new String[]{e.getMessage()});
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE:
                        finish();
                        break;
                }
            }
        };
        AlertDialogFactory f = AlertDialogFactory.getBasicAlertFactoryWithIcon(this, title,
                message, android.R.drawable.ic_dialog_info, listener);
        showAlertDialog(f);
    }

    @Override
    public void taskCancelled() {

    }

    public void cancelCurrentTask() {
        stateHolder.cancelTask();
    }

    protected void restoreLastQueryString() {
        lastQueryString = CommCareApplication._().getCurrentSession().getCurrentFrameStepExtra(KEY_LAST_QUERY_STRING);
    }

    protected void saveLastQueryString() {
        CommCareApplication._().getCurrentSession().addExtraToCurrentFrameStep(KEY_LAST_QUERY_STRING, lastQueryString);
    }

    //Graphical stuff below, needs to get modularized

    protected void transplantStyle(TextView target, int resource) {
        //get styles from here
        TextView tv = (TextView) View.inflate(this, resource, null);
        int[] padding = {target.getPaddingLeft(), target.getPaddingTop(), target.getPaddingRight(), target.getPaddingBottom()};

        target.setTextColor(tv.getTextColors().getDefaultColor());
        target.setTypeface(tv.getTypeface());
        target.setBackgroundDrawable(tv.getBackground());
        target.setPadding(padding[0], padding[1], padding[2], padding[3]);
    }

    /**
     * The right-hand side of the title associated with this activity.
     * <p/>
     * This will update dynamically as the activity loads/updates, but if
     * it will ever have a value it must return a blank string when one
     * isn't available.
     */
    protected String getActivityTitle() {
        return null;
    }

    public static String getTopLevelTitleName(Context c) {
        try {
            return Localization.get("app.display.name");
        } catch (NoLocalizedTextException nlte) {
            return c.getString(org.commcare.dalvik.R.string.title_bar_name);
        }
    }

    protected static String getTitle(Context c, String local) {
        String topLevel = getTopLevelTitleName(c);

        String[] stepTitles = new String[0];
        try {
            stepTitles = CommCareApplication._().getCurrentSession().getHeaderTitles();

            //See if we can insert any case hacks
            int i = 0;
            for (StackFrameStep step : CommCareApplication._().getCurrentSession().getFrame().getSteps()) {
                try {
                    if (SessionFrame.STATE_DATUM_VAL.equals(step.getType())) {
                        //Haaack
                        if (step.getId() != null && step.getId().contains("case_id")) {
                            ACase foundCase = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class).getRecordForValue(ACase.INDEX_CASE_ID, step.getValue());
                            stepTitles[i] = Localization.get("title.datum.wrapper", new String[]{foundCase.getName()});
                        }
                    }
                } catch (Exception e) {
                    //TODO: Your error handling is bad and you should feel bad
                }
                ++i;
            }
        } catch (SessionStateUninitException e) {

        }

        StringBuilder titleBuf = new StringBuilder(topLevel);
        for (String title : stepTitles) {
            if (title != null) {
                titleBuf.append(" > ").append(title);
            }
        }

        if (local != null) {
            titleBuf.append(" > ").append(local);
        }
        return titleBuf.toString();
    }

    protected boolean isNetworkNotConnected() {
        return !ConnectivityStatus.isNetworkAvailable(this);
    }

    // region - All methods for implementation of DialogController

    @Override
    public void updateProgress(String updateText, int taskId) {
        CustomProgressDialog mProgressDialog = getCurrentProgressDialog();
        if (mProgressDialog != null && !areFragmentsPaused) {
            if (mProgressDialog.getTaskId() == taskId) {
                mProgressDialog.updateMessage(updateText);
            } else {
                warnInvalidProgressUpdate(taskId);
            }
        }
    }

    public void hideTaskCancelButton() {
        CustomProgressDialog mProgressDialog = getCurrentProgressDialog();
        if (mProgressDialog != null) {
            mProgressDialog.removeCancelButton();
        }
    }

    @Override
    public void updateProgressBar(int progress, int max, int taskId) {
        CustomProgressDialog mProgressDialog = getCurrentProgressDialog();
        if (mProgressDialog != null && !areFragmentsPaused) {
            if (mProgressDialog.getTaskId() == taskId) {
                mProgressDialog.updateProgressBar(progress, max);
            } else {
                warnInvalidProgressUpdate(taskId);
            }
        }
    }

    private void warnInvalidProgressUpdate(int taskId) {
        String message = "Attempting to update a progress dialog whose taskId (" + taskId +
                " does not match the task for which the update message was intended.";

        if(invalidTaskIdMessageThrown != taskId) {
            invalidTaskIdMessageThrown = taskId;
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, message);
        } else {
            Log.w(TAG, message);
        }
    }

    @Override
    public void showProgressDialog(int taskId) {
        if (taskId >= 0) {
            CustomProgressDialog dialog = generateProgressDialog(taskId);
            if (dialog != null) {
                dialog.show(getSupportFragmentManager(), KEY_PROGRESS_DIALOG_FRAG);
            }
        }
    }

    @Override
    public CustomProgressDialog getCurrentProgressDialog() {
        return (CustomProgressDialog) getSupportFragmentManager().
                findFragmentByTag(KEY_PROGRESS_DIALOG_FRAG);
    }

    @Override
    public void dismissProgressDialog() {
        CustomProgressDialog progressDialog = getCurrentProgressDialog();
        if (progressDialog != null && progressDialog.isAdded()) {
            if (areFragmentsPaused) {
                triedDismissingWhilePaused = true;
            } else {
                progressDialog.dismiss();
            }
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        //dummy method for compilation, implementation handled in those subclasses that need it
        return null;
    }

    @Override
    public AlertDialogFragment getCurrentAlertDialog() {
        return (AlertDialogFragment) getSupportFragmentManager().
                findFragmentByTag(KEY_ALERT_DIALOG_FRAG);
    }

    @Override
    public void showPendingAlertDialog() {
        if (alertDialogToShowOnResume != null && getCurrentAlertDialog() == null) {
            alertDialogToShowOnResume.show(getSupportFragmentManager(), KEY_ALERT_DIALOG_FRAG);
            alertDialogToShowOnResume = null;
        } else {
            showPendingUserMessage();
        }
    }

    @Override
    public void showAlertDialog(AlertDialogFactory f) {
        if (getCurrentAlertDialog() != null) {
            // Means we already have an alert dialog on screen
            return;
        }
        AlertDialogFragment dialog = AlertDialogFragment.fromFactory(f);
        if (areFragmentsPaused) {
            alertDialogToShowOnResume = dialog;
        } else {
            dialog.show(getSupportFragmentManager(), KEY_ALERT_DIALOG_FRAG);
        }
    }

    // endregion


    public Pair<Detail, TreeReference> requestEntityContext() {
        return null;
    }

    /**
     * Interface to perform additional setup code when adding an ActionBar
     * using the {@link #tryToAddActionSearchBar(android.app.Activity,
     * android.view.Menu,
     * CommCareActivity.ActionBarInstantiator)}
     * tryToAddActionSearchBar} method.
     */
    public interface ActionBarInstantiator {
        void onActionBarFound(MenuItem searchItem, SearchView searchView, MenuItem barcodeItem);
    }

    /**
     * Tries to add actionBar to current Activity and hides the current search
     * widget and runs ActionBarInstantiator if it exists. Used in
     * EntitySelectActivity and FormRecordListActivity.
     *
     * @param act          Current activity
     * @param menu         Menu passed through onCreateOptionsMenu
     * @param instantiator Optional ActionBarInstantiator for additional setup
     *                     code.
     */
    protected void tryToAddActionSearchBar(Activity act, Menu menu,
                                           ActionBarInstantiator instantiator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            MenuInflater inflater = act.getMenuInflater();
            inflater.inflate(org.commcare.dalvik.R.menu.activity_report_problem, menu);

            MenuItem searchItem = menu.findItem(org.commcare.dalvik.R.id.search_action_bar);
            SearchView searchView =
                    (SearchView) searchItem.getActionView();
            MenuItem barcodeItem = menu.findItem(org.commcare.dalvik.R.id.barcode_scan_action_bar);
            if (searchView != null) {
                int[] searchViewStyle =
                        AndroidUtil.getThemeColorIDs(this,
                                new int[]{org.commcare.dalvik.R.attr.searchbox_action_bar_color});
                int id = searchView.getContext()
                        .getResources()
                        .getIdentifier("android:id/search_src_text", null, null);
                TextView textView = (TextView) searchView.findViewById(id);
                textView.setTextColor(searchViewStyle[0]);
                if (instantiator != null) {
                    instantiator.onActionBarFound(searchItem, searchView, barcodeItem);
                }
            }

            View bottomSearchWidget = act.findViewById(org.commcare.dalvik.R.id.searchfooter);
            if (bottomSearchWidget != null) {
                bottomSearchWidget.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Whether or not the "Back" action makes sense for this activity.
     *
     * @return True if "Back" is a valid concept for the Activity ande should be shown
     * in the action bar if available. False otherwise.
     */
    public boolean isBackEnabled() {
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent mv) {
        return !(mGestureDetector == null || !mGestureDetector.onTouchEvent(mv)) || super.dispatchTouchEvent(mv);

    }

    @Override
    public boolean onDown(MotionEvent arg0) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (isHorizontalSwipe(this, e1, e2) && !isMainScreenBlocked) {
            if (velocityX <= 0) {
                return onForwardSwipe();
            }
            return onBackwardSwipe();
        }

        return false;
    }

    /**
     * Action to take when user swipes forward during activity.
     *
     * @return Whether or not the swipe was handled
     */
    protected boolean onForwardSwipe() {
        return false;
    }

    /**
     * Action to take when user swipes backward during activity.
     *
     * @return Whether or not the swipe was handled
     */
    protected boolean onBackwardSwipe() {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent arg0) {
        // ignore
    }

    @Override
    public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2, float arg3) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent arg0) {
        // ignore
    }

    @Override
    public boolean onSingleTapUp(MotionEvent arg0) {
        return false;
    }

    /**
     * Decide if two given MotionEvents represent a swipe.
     *
     * @return True iff the movement is a definitive horizontal swipe.
     */
    private static boolean isHorizontalSwipe(Activity activity, MotionEvent e1, MotionEvent e2) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);

        //details of the motion itself
        float xMov = Math.abs(e1.getX() - e2.getX());
        float yMov = Math.abs(e1.getY() - e2.getY());

        double angleOfMotion = ((Math.atan(yMov / xMov) / Math.PI) * 180);


        // for all screens a swipe is left/right of at least .25" and at an angle of no more than 30
        //degrees
        int xPixelLimit = (int) (dm.xdpi * .25);

        return xMov > xPixelLimit && angleOfMotion < 30;
    }

    /**
     * Rebuild the activity's menu options based on the current state of the activity.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void rebuildOptionMenu() {
        if (CommCareApplication._().getCurrentApp() != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                invalidateOptionsMenu();
            } else {
                supportInvalidateOptionsMenu();
            }
        }
    }

    public Spannable localize(String key) {
        return MarkupUtil.localizeStyleSpannable(this, key);
    }

    public Spannable localize(String key, String arg) {
        return MarkupUtil.localizeStyleSpannable(this, key, arg);
    }


    public Spannable localize(String key, String[] args) {
        return MarkupUtil.localizeStyleSpannable(this, key, args);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void refreshActionBar() {
        FragmentManager fm = this.getSupportFragmentManager();
        BreadcrumbBarFragment bar = (BreadcrumbBarFragment) fm.findFragmentByTag("breadcrumbs");
        bar.refresh(this);
    }

    /**
     * Activity has been put in the background. Useful in knowing when to not
     * perform dialog or fragment transactions
     */
    protected boolean areFragmentsPaused() {
        return areFragmentsPaused;
    }

    public void setMainScreenBlocked(boolean isBlocked) {
        isMainScreenBlocked = isBlocked;
    }

    private boolean usesUIController() {
        return this instanceof WithUIController;
    }

    public Object getUIManager() {
        if (usesUIController()) {
            return ((WithUIController)this).getUIController();
        } else {
            return this;
        }
    }

    private boolean isManagedUiActivity() {
        return ManagedUiFramework.isManagedUi(getUIManager().getClass());
    }

    public void setStateHolder(TaskConnectorFragment<R> stateHolder) {
        this.stateHolder = stateHolder;
    }
}

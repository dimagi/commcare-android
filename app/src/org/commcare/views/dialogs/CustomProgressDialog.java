package org.commcare.views.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Any progress dialog associated with a CommCareTask should use
 * this class to implement the dialog. Any class that launches such a task
 * should implement the generateProgressDialog() method of the DialogController
 * interface, and create the dialog in that method. The rest of the dialog's
 * lifecycle is handled by methods of the DialogController interface that are
 * fully implemented in CommCareActivity
 *
 * @author amstone326
 */
public class CustomProgressDialog extends DialogFragment {

    //keys for onSaveInstanceState
    private final static String KEY_TITLE = "title";
    private final static String KEY_MESSAGE = "message";
    private final static String KEY_USING_CB = "using_checkbox";
    private final static String KEY_IS_CHECKED = "is_checked";
    private final static String KEY_CB_TEXT = "checkbox_text";
    private final static String KEY_USING_BUTTON = "using_cancel_buton";
    private final static String KEY_TASK_ID = "task_id";
    private final static String KEY_CANCELABLE = "is_cancelable";
    private final static String KEY_WAS_CANCEL_PRESSED = "was_cancel_pressed";
    private final static String KEY_USING_PROGRESS_BAR = "using_progress_bar";
    private final static String KEY_PROGRESS_BAR_PROGRESS = "progress_bar_progress";
    private final static String KEY_PROGRESS_BAR_MAX = "progress_bar_max";

    //id of the task that spawned this dialog, -1 if not associated with a CommCareTask
    private int taskId;

    //for all dialogs
    private String title;
    private String message;
    private boolean isCancelable; //default is false, only set to true if setCancelable() is explicitly called
    private boolean wasCancelPressed;

    //for checkboxes
    private boolean usingCheckbox;
    private boolean isChecked;
    private String checkboxText;

    //for cancel button
    private boolean usingCancelButton;
    private Button cancelButton;

    //for progress bar
    private boolean usingHorizontalProgressBar;
    private int progressBarProgress;
    private int progressBarMax;

    public static CustomProgressDialog newInstance(String title, String message, int taskId) {
        CustomProgressDialog frag = new CustomProgressDialog();
        frag.title = title;
        frag.message = message;
        frag.taskId = taskId;
        return frag;
    }

    public void addCheckbox(String text, boolean isChecked) {
        this.usingCheckbox = true;
        this.checkboxText = text;
        this.isChecked = isChecked;
    }

    public void addCancelButton() {
        usingCancelButton = true;
    }

    public void setCancelable() {
        this.isCancelable = true;
    }

    public void addProgressBar() {
        this.usingHorizontalProgressBar = true;
        this.progressBarProgress = 0;
        this.progressBarMax = 0;
    }

    public int getTaskId() {
        return taskId;
    }

    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreFields(savedInstanceState);
    }

    private void restoreFields(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            this.title = savedInstanceState.getString(KEY_TITLE);
            this.message = savedInstanceState.getString(KEY_MESSAGE);
            this.usingCheckbox = savedInstanceState.getBoolean(KEY_USING_CB);
            this.isChecked = savedInstanceState.getBoolean(KEY_IS_CHECKED);
            this.checkboxText = savedInstanceState.getString(KEY_CB_TEXT);
            this.usingCancelButton = savedInstanceState.getBoolean(KEY_USING_BUTTON);
            this.taskId = savedInstanceState.getInt(KEY_TASK_ID);
            this.isCancelable = savedInstanceState.getBoolean(KEY_CANCELABLE);
            this.wasCancelPressed = savedInstanceState.getBoolean(KEY_WAS_CANCEL_PRESSED);
            this.usingHorizontalProgressBar = savedInstanceState.getBoolean(KEY_USING_PROGRESS_BAR);
            this.progressBarProgress = savedInstanceState.getInt(KEY_PROGRESS_BAR_PROGRESS);
            this.progressBarMax = savedInstanceState.getInt(KEY_PROGRESS_BAR_MAX);
        }
    }

    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }


    /* Creates the dialog that will reside within the fragment */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        restoreFields(savedInstanceState);
        Context context = getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        setCancelable(isCancelable);

        View view;
        if (usingHorizontalProgressBar) {
            view = LayoutInflater.from(context).inflate(R.layout.progress_dialog_determinate, null);
            setupDeterminateView(view);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.progress_dialog_indeterminate, null);
        }

        TextView titleView = (TextView)view.findViewById(R.id.progress_dialog_title).findViewById(R.id.dialog_title_text);
        titleView.setText(title);
        TextView messageView = (TextView)view.findViewById(R.id.progress_dialog_message);
        messageView.setText(message);

        setupCancelButton(view);
        if (wasCancelPressed) {
            setCancellingText(titleView, messageView, cancelButton);
        }

        builder.setView(view);
        Dialog d = builder.create();
        d.setCanceledOnTouchOutside(isCancelable);

        return d;
    }

    private void setupDeterminateView(View view) {
        ProgressBar bar = (ProgressBar)view.findViewById(R.id.progress_bar_horizontal);
        bar.setProgress(progressBarProgress);
        bar.setMax(progressBarMax);

        if (usingCheckbox) {

            CheckBox cb = (CheckBox)view.findViewById(R.id.progress_dialog_checkbox);
            cb.setVisibility(View.VISIBLE);
            cb.setText(checkboxText);
            cb.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    isChecked = ((CheckBox)v).isChecked();
                }

            });
            if (isChecked) {
                cb.toggle();
            }
        }
    }

    private void setupCancelButton(View v) {
        cancelButton = (Button)v.findViewById(R.id.dialog_cancel_button);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ((CommCareActivity)getActivity()).cancelCurrentTask();
                showCancelledState();
            }
        });

        cancelButton.setVisibility(usingCancelButton ? View.VISIBLE : View.GONE);
    }

    private void showCancelledState() {
        wasCancelPressed = true;

        AlertDialog pd = (AlertDialog)getDialog();
        if (pd != null) {
            TextView titleView = (TextView)pd.findViewById(R.id.progress_dialog_title).findViewById(R.id.dialog_title_text);
            TextView messageView = (TextView)pd.findViewById(R.id.progress_dialog_message);
            Button cancelButton = (Button)pd.findViewById(R.id.dialog_cancel_button);
            setCancellingText(titleView, messageView, cancelButton);
        }
    }

    private void setCancellingText(TextView titleTextView, TextView messageTextView, Button cancelButton) {
        titleTextView.setText(Localization.get("activity.task.cancelling.title", new String[]{title}));
        messageTextView.setText(Localization.get("activity.task.cancelling.message"));
        cancelButton.setEnabled(false);
    }

    public void updateMessage(String text) {
        this.message = text;
        AlertDialog pd = (AlertDialog)getDialog();
        if (pd != null) {
            TextView tv = (TextView)pd.findViewById(R.id.progress_dialog_message);
            tv.setText(this.message);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_TITLE, this.title);
        outState.putString(KEY_MESSAGE, this.message);
        outState.putBoolean(KEY_USING_CB, this.usingCheckbox);
        outState.putBoolean(KEY_IS_CHECKED, this.isChecked);
        outState.putString(KEY_CB_TEXT, this.checkboxText);
        outState.putBoolean(KEY_USING_BUTTON, this.usingCancelButton);
        outState.putInt(KEY_TASK_ID, this.taskId);
        outState.putBoolean(KEY_CANCELABLE, this.isCancelable);
        outState.putBoolean(KEY_WAS_CANCEL_PRESSED, this.wasCancelPressed);
        outState.putBoolean(KEY_USING_PROGRESS_BAR, this.usingHorizontalProgressBar);
        outState.putInt(KEY_PROGRESS_BAR_PROGRESS, this.progressBarProgress);
        outState.putInt(KEY_PROGRESS_BAR_MAX, this.progressBarMax);
    }

    public void updateProgressBar(int progress, int max) {
        if (!usingHorizontalProgressBar) {
            return;
        }
        this.progressBarProgress = progress;
        this.progressBarMax = max;
        Dialog dialog = getDialog();
        if (dialog != null) {
            ProgressBar bar = (ProgressBar)dialog.findViewById(R.id.progress_bar_horizontal);
            bar.setProgress(progress);
            bar.setMax(max);
        }
    }

    public void setCancelButtonVisibility(boolean isVisible) {
        usingCancelButton = isVisible;
        if (cancelButton != null) {
            cancelButton.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        }
    }
}

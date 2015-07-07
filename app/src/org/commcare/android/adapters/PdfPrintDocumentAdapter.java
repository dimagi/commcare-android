package org.commcare.android.adapters;

import org.commcare.dalvik.R;

/**
 * This PdfPrintDocumentAdapter class prepares the PDF document
 * and giving the pdf to Android print frame work
 *
 * Source: https://github.com/dimagi/mWellcare-Printer/blob/master/PrinterApplication/src/org/
 *      commcare/mwellcare/PdfPrintDocumentAdapter.java
 */

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintJob;
import android.print.PrintManager;
import android.util.Log;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class PdfPrintDocumentAdapter extends PrintDocumentAdapter {

    private Activity mActivity;
    private String mFilePath;
    private String mJobName;
    private final String TAG = this.getClass().getName();
    public static final String ODK_INTENT_DATA = "odk_intent_data";
    public static final long MAXTIME_FOR_PRINT_WAIT = 120;//in sec
    public static final int PRINT_SUCCESS  = 1;
    public static final int PRINT_FAILED  = 0;
    private ProgressDialog mDialog;
    private CustomTimerTask mTimerTask;
    private Timer mTimer;
    private Resources mResources;

    public PdfPrintDocumentAdapter(Activity activity, String filePath, String jobName) {
        this.mActivity = activity;
        this.mFilePath = filePath;
        this.mJobName = jobName;
        mResources = activity.getResources();

    }
    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes,
                         PrintAttributes newAttributes,
                         CancellationSignal cancellationSignal,
                         PrintDocumentAdapter.LayoutResultCallback callback, Bundle extras) {
        // Respond to cancellation request
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            Toast.makeText(mActivity, R.string.print_error,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Return print information to print framework
        PrintDocumentInfo info = new PrintDocumentInfo
                .Builder("print_output.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build();
        // Content layout reflow is complete
        callback.onLayoutFinished(info, true);

        //callback.onLayoutFailed("Page count calculation failed.");
    }

    @Override
    public void onWrite(PageRange[] pageRanges, ParcelFileDescriptor destination,
                        CancellationSignal cancellationSignal, WriteResultCallback callback) {
        InputStream input = null;
        OutputStream output = null;

        try {
            input = new FileInputStream(mFilePath);
            output = new FileOutputStream(destination.getFileDescriptor());

            byte[] buf = new byte[1024];
            int bytesRead;

            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead);
            }

            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});

        } catch (FileNotFoundException ee){
            Log.d(TAG, "FileNotFoundException"+ee);
        } catch (Exception e) {
            Log.d(TAG, "Exception"+e);
        } finally {
            try {
                if (input!=null) {
                    input.close();
                }
                if (output!=null) {
                    output.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onFinish() {
        super.onFinish();
        PrintManager printManager = (PrintManager) mActivity.getSystemService(Context.PRINT_SERVICE);
        List<PrintJob> printJobs = printManager.getPrintJobs();
        for (int i = 0; i <printJobs.size(); i++) {
            PrintJob job = printJobs.get(i);
            if (job.getInfo().getLabel().equals(mJobName)) {
                mTimer = new Timer();
                mTimerTask = new CustomTimerTask(job);
                mTimer.scheduleAtFixedRate(mTimerTask, 0, 2000);
                break;
            }
        }
    }

    /**
     * This class will give the printjob result
     * @author rreddy.avula
     *
     */
    private class CustomTimerTask extends TimerTask {

        private PrintJob job;
        private long mTimeLimit = 2;

        public CustomTimerTask(PrintJob job) {
            this.job = job;
            mDialog = new ProgressDialog(mActivity);
            mDialog.show();
        }

        @Override
        public void run() {

            mActivity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mTimeLimit += 2;
                    if (job.isQueued()) {
                        if (!mDialog.isShowing()) {
                            mDialog.show();
                        }
                        mDialog.setTitle(mResources.getString(R.string.printjob_queued));
                    } else if(job.isStarted()) {
                        if (!mDialog.isShowing()) {
                            mDialog.show();
                        }
                        mDialog.setTitle(mResources.getString(R.string.printjob_started));
                    } else if (job.isBlocked()) {
                        mDialog.setTitle(mResources.getString(R.string.printjob_blocked));
                        job.restart();
                    } else if (job.isFailed()) {
                        mDialog.setTitle(mResources.getString(R.string.printjob_failed));
                        job.restart();
                    } else if (job.isCompleted()) {
                        job.cancel();
                        CustomTimerTask.this.cancel();
                        mTimer.cancel();
                        showAlert(mResources.getString(R.string.printing_done), PRINT_SUCCESS);
                    }
                    if (mTimeLimit == MAXTIME_FOR_PRINT_WAIT) {
                        job.cancel();
                        CustomTimerTask.this.cancel();
                        mTimer.cancel();
                        showAlert(mResources.getString(R.string.print_error), PRINT_FAILED);
                    }
                }
            });
        }
    }

    /**
     * Show the alert in the specified activity and message
     * @param msg String message that should be shown on the alert
     */
    public void showAlert(String msg, final int status) {
        AlertDialog.Builder alert=new AlertDialog.Builder(mActivity);
        alert.setTitle(msg);
        alert.setCancelable(false);
        alert.setPositiveButton(mActivity.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog1, int which) {
                Intent intent = new Intent();
                mActivity.setResult(Activity.RESULT_OK, intent);

                if (mDialog.isShowing()) {
                    mDialog.dismiss();
                }
                if (status == PRINT_SUCCESS) {
                    intent.putExtra(ODK_INTENT_DATA, true);
                } else if (status == PRINT_FAILED) {
                    intent.putExtra(ODK_INTENT_DATA, false);
                }
                mActivity.finish();
            }
        });
        alert.show();
    }
    /**
     * This cancels the all print jobs currently irrespective of
     * their status
     */
    public void cancelTimer() {
        if (mTimerTask!=null) {
            mTimerTask.cancel();
            mTimer.cancel();
            if (mDialog!=null && mDialog.isShowing()) {
                mDialog.dismiss();
            }
            Intent intent = new Intent();
            mActivity.setResult(Activity.RESULT_OK, intent);
            intent.putExtra(ODK_INTENT_DATA, false);
            mActivity.finish();
        }

    }
}

package org.commcare.dalvik.activities;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.commcare.android.tasks.TemplatePrinterTask;
import org.commcare.android.tasks.TemplatePrinterTask.PopulateListener;
import org.commcare.android.util.TemplatePrinterUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;


/**
 * Intermediate activity which populates an HTML template with data and then prints it
 * 
 * @author Richard Lu
 * @author amstone
 */
public class TemplatePrinterActivity extends Activity implements PopulateListener {

    /**
     * The path to the temp file location that is written to in TemplatePrinterTask, and then
     * read back from in doHtmlPrint()
     */
    private String outputPath;

    /**
     * Unique name to use for the print job name
     */
    private static String jobName;

    /**
     * Used to hold an instance of the WebView object being printed, so that is it not garbage
     * collected before the print job is created
     */
    private WebView mWebView;

    /**
     * Indicates whether a print job has been started, used to determine if this activity should
     * finish under certain conditions
     */
    private boolean jobStarted;

    private PrintJob printJob;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_template_printer);

        //Check to make sure we are targeting API 19 or above, which is where print is supported
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            showErrorDialog(R.string.print_not_supported);
        }

        Bundle data = getIntent().getExtras();
        //Check to make sure key-value data has been passed with the intent
        if (data == null) {
            showErrorDialog(R.string.no_data);
        }

        this.outputPath = CommCareApplication._().getTempFilePath() + ".html";

        //Check if a doc location is coming in from the Intent
        //Will return a reference of format jr://... if it has been set
        String path = data.getString("cc:print_template_reference");
        if (path != null) {
            try {
                path = ReferenceManager._().DeriveReference(path).getLocalURI();
                preparePrintDoc(path);
            } catch (InvalidReferenceException e) {
                showErrorDialog(getString(R.string.template_invalid, path));
            }
        } else {
            //Try to use the document location that was set in Settings menu
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            path = prefs.getString(CommCarePreferences.PRINT_DOC_LOCATION, "");
            if ("".equals(path)) {
                showErrorDialog(R.string.template_not_set);
            } else {
                preparePrintDoc(path);
            }
        }
    }

    private void preparePrintDoc(String inputPath) {
        generateJobName(inputPath);
        String extension = TemplatePrinterUtils.getExtension(inputPath);
        File templateFile = new File(inputPath);
        if (extension.equalsIgnoreCase("html") && templateFile.exists()) {
            new TemplatePrinterTask(
                    templateFile,
                    outputPath,
                    getIntent().getExtras(),
                    this
            ).execute();
        } else {
            showErrorDialog(getString(R.string.template_invalid, inputPath));
        }
    }

    /**
     * Generate a unique name for this print job, using the name of the template file and the date
     *
     * @param templateFilename the path to the given template file
     */
    private void generateJobName(String templateFilename) {
        String inputWithoutExtension = templateFilename.substring(0,
                templateFilename.lastIndexOf('.'));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String dateString = sdf.format(new Date());
        jobName = inputWithoutExtension + "_" + dateString;
    }

    /**
     * Called when TemplatePrinterTask encounters an error and displays the correct error message
     * depending on the type of error that was encountered
     *
     * @param errorType indicates the type of error encountered in TemplatePrinterTask
     */
    @Override
    public void onError(int errorType) {
        if (errorType ==  TemplatePrinterTask.IO_ERROR) {
            showErrorDialog(R.string.print_io_error);
        } else {
            showErrorDialog(R.string.template_malformed);
        }
    }

    /**
     * Called when TemplatePrinterTask finishes successfully, meaning a .html file of the
     * filled-out template has been created and saved successfully
     */
    @Override
    public void onFinished() {
        doHtmlPrint();
    }

    private void showErrorDialog(int messageResId) {
        showErrorDialog(getString(messageResId));
    }

    /**
     * Displays an error dialog with the specified message.
     * Activity will quit upon exiting the dialog.
     *
     * @param message Error message
     */
    private void showErrorDialog(String message) {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                .setTitle(R.string.error_occured)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                finish();
                            }
                        }
                );
        dialogBuilder.show();
    }

    /**
     * Shows an alert dialog about the status of the print job.
     * Activity will quit upon exiting the dialog.
     *
     * @param msg String message that should be shown on the alert
     */
    private void showAlertDialog(String msg) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(msg);
        alert.setCancelable(false);
        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });

        alert.show();
    }

    /**
     * Prepares a WebView of the html document generated by this activity, which can then be
     * printed by the Android print framework
     *
     * Source: https://developer.android.com/training/printing/html-docs.html
     */
    private void doHtmlPrint() {
        // Create a WebView object specifically for printing
        WebView webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient() {

            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                createWebPrintJob(view);
                mWebView = null;
            }
        });
        try {
            String htmlDocString = TemplatePrinterUtils.readStringFromFile(outputPath);
            webView.loadDataWithBaseURL(null, htmlDocString, "text/HTML", "UTF-8", null);
            // Keep reference to WebView object until PrintDocumentAdapter is passed to PrintManager
            mWebView = webView;
        } catch (IOException e) {
            showErrorDialog(R.string.print_io_error);
        }

    }

    /**
     * Starts a print job for the given WebView
     *
     * Source: https://developer.android.com/training/printing/html-docs.html
     *
     * @param v the WebView to be printed
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void createWebPrintJob(WebView v) {

        // Get a PrintManager instance
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);

        // Get a print adapter instance
        PrintDocumentAdapter printAdapter = v.createPrintDocumentAdapter();

        // Create a print job with name and adapter instance
        printJob = printManager.print(jobName, printAdapter,
                new PrintAttributes.Builder().build());
        jobStarted = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (jobStarted) {
            reportJobResult();
        }
    }

    /**
     * If we have reached onResume after a job was started, that means that the external
     * Android print dialog has closed, either because the user pressed 'back' or because the
     * job actually finished. We therefore want to check the status of the job and report back
     * on it with an alert (activity will finish when AlertDialog closes)
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void reportJobResult() {
        if (printJob.isCompleted()) {
            showAlertDialog(getString(R.string.printing_done));
        } else if (printJob.isFailed()) {
            showAlertDialog(getString(R.string.print_error));
        } else {
            showAlertDialog(getString(R.string.printjob_not_started));
        }
    }

}
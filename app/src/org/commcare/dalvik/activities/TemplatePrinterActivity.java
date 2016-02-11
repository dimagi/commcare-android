package org.commcare.dalvik.activities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintJobInfo;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.commcare.android.tasks.TemplatePrinterTask;
import org.commcare.android.tasks.TemplatePrinterTask.PopulateListener;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.TemplatePrinterUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;
import org.odk.collect.android.utilities.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * Intermediate activity which populates an HTML template with data and then prints it
 *
 * @author Richard Lu
 * @author amstone
 */
public class TemplatePrinterActivity extends Activity implements PopulateListener {

    private static final String KEY_TEMPLATE_STYLE = "PRINT_TEMPLATE_STYLE";
    private static final String TEMPLATE_STYLE_HTML = "TEMPLATE_HTML";
    private static final String TEMPLATE_STYLE_ZPL = "TEMPLATE_ZPL";

    private static final int CALLOUT_ZPL = 1;


    /**
     * The path to the temp file location that is written to in TemplatePrinterTask, and then
     * read back from in doHtmlPrint()
     */
    private String outputPath;

    /**
     * Unique name to use for the print job name
     */
    private String jobName;

    private PrintJob printJob;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_template_printer);

        String path = getPathOrThrowError();

        //A null return code from the path retriever means that it is displaying a message;
        if(path == null) {
            return;
        }

        String printStyle = this.getIntent().getExtras().getString(KEY_TEMPLATE_STYLE);
        if(printStyle == null) {
            printStyle = TEMPLATE_STYLE_HTML;
        }

        if(TEMPLATE_STYLE_ZPL.equals(printStyle)) {
            File file = new File(path);

            doZebraPrint(path);
            return;
        }


        //Check to make sure we are targeting API 19 or above, which is where print is supported
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            showErrorDialog(Localization.get("print.not.supported"));
            return;
        }


        this.outputPath = CommCareApplication._().getTempFilePath() + ".html";

        preparePrintDoc(path);
    }

    private void doZebraPrint(String path) {

        //move file temporarily to globally readable spot
        File oldPath = new File(path);
        File newDest = Environment.getExternalStorageDirectory();

        File destFile = new File(newDest, oldPath.getName());

        FileUtils.copyFile(oldPath, destFile);

        Intent i = new Intent("com.dimagi.android.zebraprinttool.action.PrintTemplate");
        i.putExtra("zebra:template_file_path", destFile.getAbsolutePath());
        i.putExtras(this.getIntent().getExtras());
        this.startActivityForResult(i, CALLOUT_ZPL);
    }

    /**
     * Retrieve a valid path that is the template file to be used during printing, or
     * display an error message to the user. If a message is displayed, the method will
     * return null and the activity should not continue attempting to print
     */
    private String getPathOrThrowError() {
        Bundle data = getIntent().getExtras();

        //Check to make sure key-value data has been passed with the intent
        if (data == null) {
            showErrorDialog(Localization.get("no.print.data"));
            return null;
        }

        //Check if a doc location is coming in from the Intent
        //Will return a reference of format jr://... if it has been set
        String path = data.getString("cc:print_template_reference");
        if (path != null) {
            try {
                path = ReferenceManager._().DeriveReference(path).getLocalURI();
                return path;
            } catch (InvalidReferenceException e) {
                showErrorDialog(Localization.get("template.invalid"));
                return null;
            }
        } else {
            //Try to use the document location that was set in Settings menu
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            path = prefs.getString(CommCarePreferences.PREFS_PRINT_DOC_LOCATION, "");
            if ("".equals(path)) {
                showErrorDialog(Localization.get("missing.template.file"));
                return null;
            } else {
                return path;
            }
        }
    }

    private void preparePrintDoc(String inputPath) {
        generateJobName(inputPath);
        String extension = FileUtils.getExtension(inputPath);
        File templateFile = new File(inputPath);
        if (extension.equalsIgnoreCase("html") && templateFile.exists()) {
            new TemplatePrinterTask(
                    templateFile,
                    outputPath,
                    getIntent().getExtras(),
                    this
            ).execute();
        } else {
            showErrorDialog(Localization.get("template.invalid"));
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
     * Called when TemplatePrinterTask finishes, with a result code indicating what happened. If
     * a .html file of the filled-out template has been created and saved successfully, proceeds
     * with printing. Otherwise, displays the appropriate error message.
     */
    @Override
    public void onPopulationFinished(TemplatePrinterTask.PrintTaskResult result, String problemString) {
        switch (result) {
            case SUCCESS:
                doHtmlPrint();
                break;
            case IO_ERROR:
                showErrorDialog(Localization.get("print.io.error"));
                break;
            case VALIDATION_ERROR_MUSTACHE:
                showErrorDialog(Localization.get("template.malformed.mustache", new String[]{problemString}));
                break;
            case VALIDATION_ERROR_CHEVRON:
                showErrorDialog(Localization.get("template.malformed.chevron", new String[]{problemString}));
        }
    }

    private void showErrorDialog(String message) {
        TemplatePrinterUtils.showAlertDialog(this, getString(R.string.error_occured), message, true);
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
            }
        });
        try {
            String htmlDocString = TemplatePrinterUtils.readStringFromFile(outputPath);
            webView.loadDataWithBaseURL(null, htmlDocString, "text/HTML", "UTF-8", null);
        } catch (IOException e) {
            showErrorDialog(Localization.get("print.io.error"));
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CALLOUT_ZPL) {
            this.finish();
            return;
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
        PrintManager printManager = (PrintManager)getSystemService(Context.PRINT_SERVICE);

        // Get a print adapter instance
        PrintDocumentAdapter printAdapter = new PrintDocumentAdapterWrapper(this, v.createPrintDocumentAdapter());

        // Create a print job with name and adapter instance
        printJob = printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
    }

    /**
     * A wrapper for the default print document adapter generated for a web view, to enable
     * implementation of a custom callback when onFinish() is called
     *
     * Source: http://stackoverflow.com/questions/30742051/android-printmanager-get-callback
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    class PrintDocumentAdapterWrapper extends PrintDocumentAdapter {

        private final PrintDocumentAdapter delegate;
        private final Activity activity;

        public PrintDocumentAdapterWrapper(Activity activity, PrintDocumentAdapter adapter) {
            super();
            this.activity = activity;
            this.delegate = adapter;
        }

        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                             CancellationSignal cancellationSignal,
                             PrintDocumentAdapter.LayoutResultCallback callback, Bundle extras) {
            delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras);
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                            CancellationSignal cancellationSignal,
                            PrintDocumentAdapter.WriteResultCallback callback) {
            delegate.onWrite(pages, destination, cancellationSignal, callback);
        }

        @Override
        public void onFinish() {
            delegate.onFinish();
            String printDialogTitle = Localization.get("print.dialog.title");
            String msg = "";
            boolean printInitiated = false;
            switch (printJob.getInfo().getState()) {
                case PrintJobInfo.STATE_BLOCKED:
                    msg = Localization.get("printjob.blocked");
                    break;
                case PrintJobInfo.STATE_CANCELED:
                    msg = Localization.get("printjob.not.started");
                    break;
                case PrintJobInfo.STATE_COMPLETED:
                    msg = Localization.get("printing.done");
                    printInitiated = true;
                    break;
                case PrintJobInfo.STATE_FAILED:
                    msg = Localization.get("print.error");
                    break;
                case PrintJobInfo.STATE_CREATED:
                case PrintJobInfo.STATE_QUEUED:
                case PrintJobInfo.STATE_STARTED:
                    msg = Localization.get("printjob.started");
                    printInitiated = true;
            }
            TemplatePrinterUtils.showPrintStatusDialog(activity, printDialogTitle, msg,
                    printInitiated);
        }
    }

}
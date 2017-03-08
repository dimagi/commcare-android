package org.commcare.print;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintJobInfo;
import android.print.PrintManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.commcare.CommCareApplication;
import org.commcare.android.javarosa.IntentCallout;
import org.commcare.dalvik.R;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.suite.model.Detail;
import org.commcare.print.TemplatePrinterTask.PopulateListener;
import org.commcare.utils.CompoundIntentList;
import org.commcare.utils.FileUtil;
import org.commcare.utils.TemplatePrinterUtils;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    public static final String KEY_GRAPH_TO_PRINT = "graph-html-to-print";
    public static final String PRINT_TEMPLATE_REF_STRING = "cc:print_template_reference";

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

        String printStyle = this.getIntent().getExtras().getString(KEY_TEMPLATE_STYLE);
        if (printStyle == null) {
            if(CompoundIntentList.isIntentCompound(this.getIntent())) {
                //Only zebra print jobs can compound
                //TODO: This still isn't a particularly great way for us to be differentiating
                printStyle = TEMPLATE_STYLE_ZPL;
            } else {
                printStyle = TEMPLATE_STYLE_HTML;
            }
        }

        if (TEMPLATE_STYLE_ZPL.equals(printStyle)) {

            //Since this and the callout activities are raised as "dialog" activities, they will
            //recreate themselves on rotation. If we detect that we need to not "re-kick-off" the
            //activity, it will result in duplicate activities.
            if (savedInstanceState != null) {
                return;
            } else {
                doZebraPrint();
                return;
            }
        }

        String pathToTemplateFile = getTemplateFilePathOrThrowError(getIntent().getExtras());

        // A null return code from the path retriever means that it is displaying a message;
        if (pathToTemplateFile == null) {
            return;
        }

        // Check to make sure we are targeting API 19 or above, which is where print is supported
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            showErrorDialog(Localization.get("print.not.supported"));
            return;
        }

        this.outputPath = CommCareApplication.instance().getTempFilePath() + ".html";
        preparePrintDoc(pathToTemplateFile);
    }

    private void doZebraPrint() {
        Intent i = new Intent("com.dimagi.android.zebraprinttool.action.PrintTemplate");

        if (CompoundIntentList.isIntentCompound(this.getIntent())) {
            ArrayList<String> keys = this.getIntent().getStringArrayListExtra(
                    CompoundIntentList.EXTRA_COMPOUND_DATA_INDICES);
            i.putStringArrayListExtra("zebra:bundle_list", keys);
            for(String key : keys) {
                Bundle b = this.getIntent().getBundleExtra(key);
                prepareZebraBundleFromFile(b);
                i.putExtra(key, b);
            }
        } else {
            String key = "single_job";
            Bundle intentBundle = this.getIntent().getExtras();
            prepareZebraBundleFromFile(intentBundle);
            i.putExtra(key, intentBundle);
            ArrayList<String> extraKeys = new ArrayList<>();
            extraKeys.add(key);
            i.putStringArrayListExtra("zebra:bundle_list", extraKeys);
        }
        this.startActivityForResult(i, CALLOUT_ZPL);
    }

    private void prepareZebraBundleFromFile(Bundle bundle) {
        String path = getTemplateFilePathOrThrowError(bundle);

        File destFile = new File(path);
        bundle.putString("zebra:template_file_path", destFile.getAbsolutePath());
    }

    /**
     * Retrieve a valid path that is the template file to be used during printing, or
     * display an error message to the user. If a message is displayed, the method will
     * return null and the activity should not continue attempting to print
     */
    private String getTemplateFilePathOrThrowError(Bundle data) {
        // Check to make sure key-value data has been passed with the intent
        if (data == null) {
            showErrorDialog(Localization.get("no.print.data"));
            return null;
        }

        // Check if a doc location is coming in from the Intent
        // Will return a reference of format jr://... if it has been set
        String path = data.getString(PRINT_TEMPLATE_REF_STRING);
        if (path != null && !path.equals(Detail.PRINT_TEMPLATE_PROVIDED_VIA_GLOBAL_SETTING)) {
            try {
                path = ReferenceManager.instance().DeriveReference(path).getLocalURI();
                return path;
            } catch (InvalidReferenceException e) {
                showErrorDialog(Localization.get("template.invalid"));
                return null;
            }
        } else {
            // Try to use the document location that was set in Settings menu
            path = CommCarePreferences.getGlobalTemplatePath();
            if (path == null) {
                showErrorDialog(Localization.get("missing.template.file"));
            }
            return path;
        }
    }

    private void preparePrintDoc(String inputPath) {
        generateJobName(inputPath);
        String extension = FileUtil.getExtension(inputPath);
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
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

        });

        try {
            String populatedHtmlDocString = TemplatePrinterUtils.readStringFromFile(outputPath);
            Document templateDoc = Jsoup.parse(populatedHtmlDocString);
            loadHtmlIntoWebView(templateDoc, webView);
        } catch (IOException e) {
            showErrorDialog(Localization.get("print.io.error"));
        }
        createWebPrintJob(webView);
    }

    private void loadHtmlIntoWebView(Document templateDoc, WebView webView) {
        String finalHTML = templateDoc.html();
        webView.loadDataWithBaseURL(null, finalHTML, "text/HTML", "UTF-8", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CALLOUT_ZPL) {
            Intent response = new Intent();
            if(resultCode != Activity.RESULT_CANCELED) {
                response.putExtra(IntentCallout.INTENT_RESULT_VALUE, "");
            }
            this.setResult(resultCode, response);
            this.finish();
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
                             LayoutResultCallback callback, Bundle extras) {
            delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras);
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                            CancellationSignal cancellationSignal,
                            WriteResultCallback callback) {
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
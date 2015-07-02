package org.commcare.dalvik.activities;

import java.io.File;

import org.commcare.android.tasks.TemplatePrinterTask;
import org.commcare.android.tasks.TemplatePrinterTask.PopulateListener;
import org.commcare.android.util.TemplatePrinterUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.MimeTypeMap;

/**
 * Intermediate activity which populates a .DOCX/.ODT template
 * with data before sending it off to a document viewer app
 * capable of printing.
 * 
 * @author Richard Lu
 */
public class TemplatePrinterActivity extends Activity implements OnClickListener, PopulateListener {
    
    private static final int REQUEST_TEMPLATE = 0;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_TEMPLATE) {

            if (resultCode == RESULT_OK
                    && data != null) {

                Uri uri = data.getData();
                executePrint(uri);

            } else {
                // No template file selected
                finish();
            }

        }

    }

    private void executePrint(Uri uri) {
        String extension = TemplatePrinterUtils.getExtension(uri.getPath());

        if (TemplatePrinterTask.DocTypeEnum.isSupportedExtension(extension)) {

            File templateFile = new File(uri.getPath());

            File outputFolder = templateFile.getParentFile();

            new TemplatePrinterTask(
                    templateFile,
                    outputFolder,
                    getIntent().getExtras(),
                    this
            ).execute();

        } else {
            showErrorDialog(
                    getString(
                            R.string.file_invalid,
                            uri.getPath()
                    )
            );
        }
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int clickId) {

        // Error dialog "OK" click
        finish();

    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_template_printer);
        
        Bundle data = getIntent().getExtras();
        
        if (data == null) {
            //TODO: Why is he doing this?
            showErrorDialog(R.string.no_data);
            return;
        } else {
            //TODO: Check if a document is coming in from the Intent -- how would this be done?
        }

        //Try to use the document location that was set in Settings menu
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String path = prefs.getString(CommCarePreferences.PRINT_DOC_LOCATION, "");
        path = "";
        Log.i("Doc location being used", path);
        File templateFile = new File(path);
        if (templateFile.exists()) {
            File outputFolder = templateFile.getParentFile();
            new TemplatePrinterTask(
                    templateFile,
                    outputFolder,
                    data,
                    this
            ).execute();
        } else {
            //TODO: instead of starting file browser, show appropriate error dialog
            // Manually select template file;
            // see onActivityResult(int,int,Intent)
            startFileBrowser();
        }
    }

    @Override
    public void onError(String message) {

        showErrorDialog(message);

    }

    @Override
    public void onFinished(File result) {

        startDocumentViewer(result);

        finish();

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
                        this
                );

        dialogBuilder.show();

    }

    /**
     * Attempts to open the document with its default viewer.
     * If default viewer is unavailable, opens a dialog from which
     * a viewer application can be selected.
     *
     * @param document Document to open
     */
    private void startDocumentViewer(File document) {

        Uri uri = Uri.fromFile(document);

        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(
                        uri.toString()
                )
        );

        Intent intent = new Intent()
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(
                        uri,
                        type == null ? "*/*" : type
                );

        startActivity(intent);

    }
    
    private void startFileBrowser() {

        Intent chooseTemplateIntent = new Intent()
                .setAction(Intent.ACTION_GET_CONTENT)
                .setType("file/*")
                .addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(
                chooseTemplateIntent,
                REQUEST_TEMPLATE
        );

    }

}

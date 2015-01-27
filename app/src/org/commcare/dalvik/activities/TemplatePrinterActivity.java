package org.commcare.dalvik.activities;

import java.io.File;

import org.commcare.android.tasks.TemplatePrinterTask;
import org.commcare.android.tasks.TemplatePrinterTask.PopulateListener;
import org.commcare.dalvik.R;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.MimeTypeMap;

public class TemplatePrinterActivity extends Activity implements OnClickListener, PopulateListener {
    
    // TODO: stop using this hack
    private static final String TEMPLATE_FILE_TEMP_PATH = "jr://file/commcare/video/data/print_template.mp4";
    private static final String TEMPLATE_FILE_PATH = "jr://file/commcare/video/data/print_template.docx";

    @Override
    public void onClick(DialogInterface dialogInterface, int clickId) {

        // Error dialog "OK" click
        finish();

    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Bundle data = getIntent().getExtras();
        
        if (data == null) {
            showErrorDialog(R.string.no_data);
            return;
        }
        
        try {
            File templateFile = new File(
                    ReferenceManager._().DeriveReference(TEMPLATE_FILE_PATH).getLocalURI()
            );
            File templateTempFile = new File(
                    ReferenceManager._().DeriveReference(TEMPLATE_FILE_TEMP_PATH).getLocalURI()
            );
            
            if (templateFile.exists() || templateTempFile.renameTo(templateFile)) {
    
                File outputFolder = templateFile.getParentFile();
    
                new TemplatePrinterTask(
                        templateFile,
                        outputFolder,
                        data,
                        this
                ).execute();
    
            } else {
                showErrorDialog(R.string.no_template);
            }
        } catch (InvalidReferenceException e) {
            showErrorDialog(e.getMessage());
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

}

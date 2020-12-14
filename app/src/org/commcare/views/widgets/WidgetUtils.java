package org.commcare.views.widgets;

import android.content.Context;
import android.content.Intent;
import android.text.Spannable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.client.android.Intents;
import com.google.zxing.integration.android.IntentIntegrator;

import org.commcare.dalvik.R;
import org.commcare.preferences.MainConfigurablePreferences;

public class WidgetUtils {
    private static final TableLayout.LayoutParams params;

    static {
        params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);
    }

    public static void setupButton(Button btn, Spannable text, int fontSize, boolean enabled) {
        btn.setText(text);
        int verticalPadding = (int)btn.getResources().getDimension(R.dimen.widget_button_padding);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize);
        btn.setPadding(20, verticalPadding, 20, verticalPadding);
        btn.setEnabled(enabled);
        btn.setLayoutParams(WidgetUtils.params);
    }

    public static Button setupClearButton(Context context, ViewGroup root, String text, int visibility) {
        Button clearButton = (Button) LayoutInflater.from(context).inflate(R.layout.blue_outlined_button, root, false);
        clearButton.setText(text);
        clearButton.setVisibility(visibility);
        return clearButton;
    }

    /**
     * Works just like {@link #createScanIntent(Context, String...)} except the format is null.
     */
    public static Intent createScanIntent(Context context) {
        return createScanIntent(context, null);
    }

    /**
     * A utility to create intent for barcode scan.
     *
     * If usage of 3rd party apps for scanning is enabled by the user, then this method will
     * create a generic intent for scanning barcodes and will fallback to zxing library if no
     * app exists to handle such intent.
     *
     * @param formats names of {@link com.google.zxing.BarcodeFormat}s to scan for. eg. {@link com.google.zxing.BarcodeFormat#QR_CODE}
     */
    public static Intent createScanIntent(Context context, String... formats) {
        if (MainConfigurablePreferences.useIntentCalloutForScanner()) {
            Intent scanIntent = new Intent(Intents.Scan.ACTION);
            if (scanIntent.resolveActivity(context.getPackageManager()) != null) {
                if (formats != null) {
                    scanIntent.putExtra(Intents.Scan.FORMATS, TextUtils.join(",", formats));
                }
                return scanIntent;
            }
        }
        IntentIntegrator intentIntegrator = new IntentIntegrator((AppCompatActivity) context);
        if (formats != null) {
            intentIntegrator.setDesiredBarcodeFormats(formats);
        }
        return intentIntegrator.createScanIntent();
    }
}

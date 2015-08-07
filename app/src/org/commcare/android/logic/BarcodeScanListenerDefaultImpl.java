package org.commcare.android.logic;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.commcare.dalvik.BuildConfig;
import org.commcare.suite.model.Callout;
import org.commcare.suite.model.CalloutData;
import org.odk.collect.android.listeners.BarcodeScanListener;

import java.util.Map;

/**
 * Created by dancluna on 8/5/15.
 */
public final class BarcodeScanListenerDefaultImpl {

    public static final String SCAN_RESULT = "SCAN_RESULT";
    public static final int BARCODE_FETCH = 1;
    public static final int CALLOUT = 3;

    public static void onBarcodeResult(BarcodeScanListener barcodeScanListener, int requestCode, int resultCode, Intent intent) {
        //region Asserting requestCode == BARCODE_FETCH
        if (BuildConfig.DEBUG && !(requestCode == BARCODE_FETCH)) {
            throw new AssertionError("requestCode should be BARCODE_FETCH!");
        }
        //endregion

        if (resultCode == Activity.RESULT_OK) {
            String result = intent.getStringExtra(SCAN_RESULT);
            barcodeScanListener.onBarcodeFetch(result, intent);
        }
    }

    public static void onCalloutResult(BarcodeScanListener barcodeScanListener, int requestCode, int resultCode, Intent intent) {
        //region Asserting requestCode == CALLOUT
        if (BuildConfig.DEBUG && !(requestCode == CALLOUT)) {
            throw new AssertionError("requestCode should be CALLOUT!");
        }
        //endregion
        if (resultCode == Activity.RESULT_OK) {
            String result = intent.getStringExtra("odk_intent_data");
            barcodeScanListener.onCalloutResult(result, intent);
        }
    }

    private static void callBarcodeScanIntent(Activity act) {
        Log.i("SCAN", "Using default barcode scan");
        Intent i = new Intent("com.google.zxing.client.android.SCAN");
        try {
            act.startActivityForResult(i, BARCODE_FETCH);
        } catch (ActivityNotFoundException anfe) {
            Toast.makeText(act,
                    "No barcode reader available! You can install one " +
                            "from the android market.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static Callout.CalloutAction makeCalloutAction(final Activity act, Callout callout, Callout.CalloutActionSetup calloutActionSetup) {
        final CalloutData calloutData = callout.evaluate();

        if (calloutData.getImage() != null && calloutActionSetup != null) {
            calloutActionSetup.onImageFound(calloutData);
        }

        final Intent i = new Intent(calloutData.getActionName());
        for (Map.Entry<String, String> keyValue : calloutData.getExtras().entrySet()) {
            i.putExtra(keyValue.getKey(), keyValue.getValue());
        }
        return new Callout.CalloutAction() {
            @Override
            public void callout() {
                Log.i("SCAN", "Using barcode scan with action: " + i.getAction());

                try {
                    act.startActivityForResult(i, CALLOUT);
                } catch (ActivityNotFoundException anfe) {
                    Toast.makeText(act, "No application found for action: " + i.getAction(), Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    public static View.OnClickListener makeCalloutOnClickListener(final Activity act, Callout callout, Callout.CalloutActionSetup calloutActionSetup) {
        if (callout == null) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callBarcodeScanIntent(act);
                }
            };
        } else {
            final Callout.CalloutAction calloutAction = makeCalloutAction(act, callout, calloutActionSetup);
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    calloutAction.callout();
                }
            };
        }
    }
}

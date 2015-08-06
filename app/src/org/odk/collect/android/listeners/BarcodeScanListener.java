package org.odk.collect.android.listeners;

import android.content.Intent;

/**
 * Created by dancluna on 8/6/15.
 */
public interface BarcodeScanListener {
    void onBarcodeFetch(String result, Intent intent);

    void onCalloutResult(String result, Intent intent);
}

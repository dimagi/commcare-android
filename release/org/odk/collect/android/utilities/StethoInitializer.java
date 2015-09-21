package org.odk.collect.android.utilities;
import android.content.Context;
import android.util.Log;

/**
 * Release version of StethoInitializer. Just does nothing, as we don't want this to run in Release builds.
 * Created by dancluna on 7/20/15.
 */
public class StethoInitializer {
    public static void initStetho(Context context){
        // does nothing in Release builds
        Log.v("stetho", "Running empty Stetho initializer!");
    }
}
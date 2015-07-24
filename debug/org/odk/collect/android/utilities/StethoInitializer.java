package org.odk.collect.android.utilities;
import android.content.Context;

import com.facebook.stetho.Stetho;

/**
 * Debug-only wrapper class that just imports and initializes Stetho. This shouldn't be called in Release builds.
 * Created by dancluna on 7/20/15.
 */
public class StethoInitializer {
    public static void initStetho(Context context){
        Stetho.initialize(
                Stetho.newInitializerBuilder(context)
                        .enableDumpapp(
                                Stetho.defaultDumperPluginsProvider(context))
                        .enableWebKitInspector(
                                Stetho.defaultInspectorModulesProvider(context))
                        .build());
    }
}

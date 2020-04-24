package org.commcare;

import android.app.Application;
import android.content.Context;
import com.facebook.flipper.android.AndroidFlipperClient;
import com.facebook.flipper.android.utils.FlipperUtils;
import com.facebook.flipper.core.FlipperClient;
import com.facebook.flipper.plugins.crashreporter.CrashReporterPlugin;
import com.facebook.flipper.plugins.databases.DatabasesFlipperPlugin;
import com.facebook.flipper.plugins.inspector.DescriptorMapping;
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin;
import com.facebook.flipper.plugins.navigation.NavigationFlipperPlugin;
import com.facebook.flipper.plugins.sharedpreferences.SharedPreferencesFlipperPlugin;
import com.facebook.soloader.SoLoader;

import org.commcare.dalvik.BuildConfig;

import java.util.Arrays;

/**
 * @author $|-|!˅@M
 */
public class FlipperHelper {

    public static void create(Application application) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        SoLoader.init(application, false);
        if (FlipperUtils.shouldEnableFlipper(application)) {
            return;
        }
        final FlipperClient flipperClient = AndroidFlipperClient.getInstance(application);
        flipperClient.addPlugin(new InspectorFlipperPlugin(application, DescriptorMapping.withDefaults()));
        String file1 = CommCareApplication.instance().getCurrentApp().getPreferencesFilename();
        String file2 = "global-preferences-filename";
        flipperClient.addPlugin(new SharedPreferencesFlipperPlugin(application,
                Arrays.asList(
                        new SharedPreferencesFlipperPlugin.SharedPreferencesDescriptor(file1, Context.MODE_PRIVATE),
                        new SharedPreferencesFlipperPlugin.SharedPreferencesDescriptor(file2, Context.MODE_PRIVATE)
                )));
        flipperClient.addPlugin(CrashReporterPlugin.getInstance());
        flipperClient.addPlugin(new DatabasesFlipperPlugin(application));
        flipperClient.addPlugin(NavigationFlipperPlugin.getInstance());
        flipperClient.start();
    }

}

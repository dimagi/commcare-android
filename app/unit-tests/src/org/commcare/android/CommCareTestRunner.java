package org.commcare.android;

import android.app.Application;
import android.os.Environment;

import org.jetbrains.annotations.NotNull;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.robolectric.DefaultTestLifecycle;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.TestLifecycle;
import org.robolectric.TestLifecycleApplication;
import org.robolectric.annotation.Config;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.shadows.ShadowEnvironment;

import java.lang.reflect.Method;

/**
 * Register sqlcipher SQLiteDatabase to be shadowed globally.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CommCareTestRunner extends RobolectricTestRunner {
    public CommCareTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    public InstrumentationConfiguration createClassLoaderConfig(final FrameworkMethod method) {
        InstrumentationConfiguration.Builder builder = new InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method));
        builder.addInstrumentedPackage("net.sqlcipher.database.SQLiteDatabase");
        builder.addInstrumentedPackage("org.commcare.models.encryption");
        return builder.build();
    }

    @NotNull
    @Override
    protected Class<? extends TestLifecycle> getTestLifecycleClass() {
        return CommCareTestLifeCycle.class;
    }

    public static class CommCareTestLifeCycle extends DefaultTestLifecycle {

        @Override public void prepareTest(final Object test) {
            if (RuntimeEnvironment.application instanceof TestLifecycleApplication) {
                ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
                super.prepareTest(test);
            }
        }
    }
}
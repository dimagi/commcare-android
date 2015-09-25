package org.commcare.android;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;

/**
 * Register sqlcipher SQLiteDatabase to be shadowed globally.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CommCareTestRunner extends RobolectricGradleTestRunner {
    public CommCareTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    public InstrumentationConfiguration createClassLoaderConfig() {
        InstrumentationConfiguration.Builder builder = InstrumentationConfiguration.newBuilder();
        builder.addInstrumentedPackage("net.sqlcipher.database.SQLiteDatabase");
        return builder.build();
    }
}
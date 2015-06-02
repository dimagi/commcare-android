/**
 * 
 */
package org.commcare.android.junit;

import org.junit.runners.model.InitializationError;
import org.robolectric.AndroidManifest;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.bytecode.ClassInfo;
import org.robolectric.bytecode.Setup;
import org.robolectric.res.Fs;

/**
 * @author ctsims
 *
 */
public class CommCareTestRunner extends RobolectricTestRunner {
    public CommCareTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    public Setup createSetup() {
        return new MySetup();
    }
}

class MySetup extends Setup
{
  // This is the only way i found how to allow instrumentation of some classes.
  // Without this shadows are not instrumented 
  @Override
  public boolean shouldInstrument(ClassInfo info) {
      boolean instrument = super.shouldInstrument(info) ||
              info.getName().equals("net.sqlcipher.database.SQLiteDatabase");
      return instrument;
  }
}



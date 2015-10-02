package org.commcare.android.tests.backend;
import org.commcare.android.shadows.SQLiteDatabaseNative;
import org.commcare.cases.model.Case;
import org.commcare.dalvik.BuildConfig;
import org.commcare.util.externalizable.AndroidClassHasher;
import org.commcare.util.externalizable.AndroidPrototypeFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;


/**
 * @author wspride
 *
 */
public class PrototypeTest {

    AndroidPrototypeFactory mFactory;


    @Before
    public void setupTests() {
        mFactory = new AndroidPrototypeFactory(null);
    }

    /**
     * Tests that hahes persist between runtimes
     */
    @Test
    public void testPrototyping() {

        mFactory.addClass(Case.class);

        AndroidClassHasher mHasher = new AndroidClassHasher();

        byte[] hash = mHasher.getClassHashValue(Case.class);

        assertEquals(Case.class, mFactory.getClass(hash));

        mFactory = new AndroidPrototypeFactory(null);
        mFactory.addClass(Case.class);

        assertEquals(Case.class, mFactory.getClass(hash));

    }

}

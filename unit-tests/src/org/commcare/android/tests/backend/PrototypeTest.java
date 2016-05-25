package org.commcare.android.tests.backend;

import org.commcare.cases.model.Case;
import org.commcare.models.AndroidClassHasher;
import org.commcare.models.AndroidPrototypeFactory;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * @author wspride
 */
public class PrototypeTest {

    private AndroidPrototypeFactory mFactory;

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

        AndroidClassHasher mHasher = AndroidClassHasher.getInstance();

        byte[] hash = mHasher.getClassHashValue(Case.class);

        assertEquals(Case.class, mFactory.getClass(hash));

        mFactory = new AndroidPrototypeFactory(null);
        mFactory.addClass(Case.class);

        assertEquals(Case.class, mFactory.getClass(hash));
    }
}

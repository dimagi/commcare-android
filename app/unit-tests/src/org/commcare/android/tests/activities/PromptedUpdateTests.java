package org.commcare.android.tests.activities;

import org.commcare.heartbeat.ApkVersion;
import org.junit.Test;

import static junit.framework.Assert.assertTrue;

/**
 * Created by amstone326 on 5/10/17.
 */

public class PromptedUpdateTests {

    @Test
    public void testApkComparator() {
        ApkVersion version2x35x1 = new ApkVersion("2.35.1");
        ApkVersion version2x35x3 = new ApkVersion("2.35.3");
        ApkVersion version2x36 = new ApkVersion("2.36");
        ApkVersion version2x36x0 = new ApkVersion("2.36.0");

        assertTrue(version2x36.compareTo(version2x36x0) == 0);
        assertTrue(version2x35x3.compareTo(version2x36x0) < 0);
        assertTrue(version2x35x3.compareTo(version2x35x1) > 0);
    }


}

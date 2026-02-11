package org.commcare.utils;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Tests for GlobalErrorUtil, specifically focusing on error pruning logic.
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class GlobalErrorUtilTest {

    @Before
    public void setup() {
        GlobalErrorUtil.dismissGlobalErrors();
    }

    @Test
    public void testErrorPruning() {
        // Create an error that is 25 hours old (should be pruned)
        long oldTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25);
        GlobalErrorRecord oldError = new GlobalErrorRecord(
                new Date(oldTime),
                GlobalErrors.PERSONALID_GENERIC_ERROR.ordinal());
        GlobalErrorUtil.addError(oldError);

        // Create an error that is 1 hour old (should be kept)
        long newTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        GlobalErrorRecord newError = new GlobalErrorRecord(
                new Date(newTime),
                GlobalErrors.PERSONALID_LOGIN_FROM_DIFFERENT_DEVICE_ERROR.ordinal());
        GlobalErrorUtil.addError(newError);

        // Check errors - this should trigger pruning
        GlobalErrors error = GlobalErrorUtil.checkGlobalErrors();

        // The old error should be gone, so we should get the new error
        Assert.assertNotNull("Should have returned a valid error", error);

        // checkGlobalErrors returns the correct error.
        Assert.assertEquals("Should return the recent error",
                GlobalErrors.PERSONALID_LOGIN_FROM_DIFFERENT_DEVICE_ERROR, error);

        // Verify storage count directly
        int count = CommCareApplication.instance().getGlobalStorage(GlobalErrorRecord.class).getNumRecords();
        Assert.assertEquals("Storage should have only 1 record left", 1, count);
    }

    @Test
    public void testNoErrors() {
        GlobalErrors error = GlobalErrorUtil.checkGlobalErrors();
        Assert.assertNull("Should return null when no errors exist", error);
    }

    @Test
    public void testDismissErrors() {
        // Add an error
        GlobalErrorRecord errorRecord = new GlobalErrorRecord(
                new Date(),
                GlobalErrors.PERSONALID_GENERIC_ERROR.ordinal());
        GlobalErrorUtil.addError(errorRecord);

        // Verify it exists AND checkGlobalErrors returns an error
        GlobalErrors error = GlobalErrorUtil.checkGlobalErrors();
        Assert.assertNotNull("Should have error before dismissal", error);

        // Dismiss
        GlobalErrorUtil.dismissGlobalErrors();

        // Verify it's gone
        GlobalErrors gone = GlobalErrorUtil.checkGlobalErrors();
        Assert.assertNull("Should return null after dismissal", gone);

        // Verify storage is empty
        int count = CommCareApplication.instance().getGlobalStorage(GlobalErrorRecord.class).getNumRecords();
        Assert.assertEquals("Storage should be empty", 0, count);
    }
}

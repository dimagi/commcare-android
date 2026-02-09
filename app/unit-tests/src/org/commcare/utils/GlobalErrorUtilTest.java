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
        String errorMessage = GlobalErrorUtil.checkGlobalErrors();

        // The old error should be gone, so we should get the new error's message
        Assert.assertNotNull("Should have returned a valid error message", errorMessage);

        // checkGlobalErrors returns the message string.
        String expectedMessage = CommCareApplication.instance().getString(
                GlobalErrors.PERSONALID_LOGIN_FROM_DIFFERENT_DEVICE_ERROR.getMessageId());

        Assert.assertEquals("Should return the recent error message", expectedMessage, errorMessage);

        // Verify storage count directly
        int count = CommCareApplication.instance().getGlobalStorage(GlobalErrorRecord.class).getNumRecords();
        Assert.assertEquals("Storage should have only 1 record left", 1, count);
    }

    @Test
    public void testNoErrors() {
        String errorMessage = GlobalErrorUtil.checkGlobalErrors();
        Assert.assertNull("Should return null when no errors exist", errorMessage);
    }

    @Test
    public void testDismissErrors() {
        // Add an error
        GlobalErrorRecord error = new GlobalErrorRecord(
                new Date(),
                GlobalErrors.PERSONALID_GENERIC_ERROR.ordinal());
        GlobalErrorUtil.addError(error);

        // Verify it exists AND checkGlobalErrors returns a message
        String errorMessage = GlobalErrorUtil.checkGlobalErrors();
        Assert.assertNotNull("Should have error before dismissal", errorMessage);

        // Dismiss
        GlobalErrorUtil.dismissGlobalErrors();

        // Verify it's gone
        String result = GlobalErrorUtil.checkGlobalErrors();
        Assert.assertNull("Should return null after dismissal", result);

        // Verify storage is empty
        int count = CommCareApplication.instance().getGlobalStorage(GlobalErrorRecord.class).getNumRecords();
        Assert.assertEquals("Storage should be empty", 0, count);
    }
}

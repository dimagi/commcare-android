package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.dalvik.BuildConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Test casting from Nepali calendar day to universal date representation.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class NepaliDateUtilitiesTests {
    @Test
    public void testTimesFallOnSameDate() {
        TimeZone nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45");

        Calendar nepaliMiddleOfDayDate = Calendar.getInstance(nepaliTimeZone);
        nepaliMiddleOfDayDate.set(2007, 10, 7, 18, 46);

        Calendar nepaliBeginningOfDayDate = Calendar.getInstance(nepaliTimeZone);
        nepaliBeginningOfDayDate.set(2007, 10, 7, 0, 0);

        UniversalDate middleOfDay = NepaliDateUtilities.fromMillis(nepaliMiddleOfDayDate.getTimeInMillis(), nepaliTimeZone);
        UniversalDate beginningOfDay = NepaliDateUtilities.fromMillis(nepaliBeginningOfDayDate.getTimeInMillis(), nepaliTimeZone);
        assertSameDate(middleOfDay, beginningOfDay);

        Calendar nepaliEndOfDayDate = Calendar.getInstance(nepaliTimeZone);
        nepaliEndOfDayDate.set(2007, 10, 7, 23, 59, 59);
        UniversalDate endOfDay = NepaliDateUtilities.fromMillis(nepaliEndOfDayDate.getTimeInMillis(), nepaliTimeZone);
        assertSameDate(endOfDay, beginningOfDay);
    }

    private static void assertSameDate(UniversalDate a, UniversalDate b) {
        Assert.assertEquals(a.day, b.day);
        Assert.assertEquals(a.month, b.month);
        Assert.assertEquals(a.year, b.year);
    }

    @Test
    public void testDateCalcsAreSensitiveToCurrentTimezone() {
        TimeZone nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45");
        TimeZone mexicanTimeZone = TimeZone.getTimeZone("GMT-06:00");
        Calendar nepalCal = Calendar.getInstance(nepaliTimeZone);
        nepalCal.set(2007, 10, 7, 18, 46);
        Calendar mexicoCal = Calendar.getInstance(mexicanTimeZone);
        mexicoCal.set(2007, 10, 7, 18, 46);

        UniversalDate mexicanDate = NepaliDateUtilities.fromMillis(mexicoCal.getTimeInMillis(), mexicanTimeZone);
        UniversalDate nepaliDate = NepaliDateUtilities.fromMillis(nepalCal.getTimeInMillis(), nepaliTimeZone);
        assertSameDate(nepaliDate, mexicanDate);
    }

    @Test
    public void testUnpackingDateInDifferentTimezone() {
        TimeZone nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45");
        TimeZone mexicanTimeZone = TimeZone.getTimeZone("GMT-06:00");
        Calendar mexicoCal = Calendar.getInstance(mexicanTimeZone);
        mexicoCal.set(2007, 10, 7, 18, 46);

        UniversalDate mexicanDate = NepaliDateUtilities.fromMillis(mexicoCal.getTimeInMillis(), mexicanTimeZone);
        long time = NepaliDateUtilities.toMillisFromJavaEpoch(mexicanDate.year, mexicanDate.month, mexicanDate.day, 0);
        UniversalDate rebuiltDateInUsingDifferentTimezone = NepaliDateUtilities.fromMillis(time, nepaliTimeZone);
        assertSameDate(rebuiltDateInUsingDifferentTimezone, mexicanDate);
    }
}

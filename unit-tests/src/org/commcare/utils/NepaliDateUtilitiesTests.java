package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.dalvik.BuildConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class NepaliDateUtilitiesTests {
    @Test
    public void testDateCasting() {
        Calendar nepalCal = Calendar.getInstance(TimeZone.getTimeZone("GMT+05:45"));
        nepalCal.set(2007, 10, 7, 18, 46);
        Calendar nepalCalRound = Calendar.getInstance(TimeZone.getTimeZone("GMT+05:45"));
        nepalCalRound.set(2007, 10, 7, 0, 0);
        Date foo = nepalCal.getTime();
        Date bar = nepalCalRound.getTime();
        System.out.print(foo);
        System.out.print(bar);

        UniversalDate nepalRoundedUniversalDate = NepaliDateUtilities.fromMillis(nepalCalRound.getTimeInMillis());
        UniversalDate universalDate = NepaliDateUtilities.fromMillis(nepalCal.getTimeInMillis());

        Assert.assertEquals(nepalRoundedUniversalDate.day, universalDate.day);
        Assert.assertEquals(nepalRoundedUniversalDate.month, universalDate.month);
        Assert.assertEquals(nepalRoundedUniversalDate.year, universalDate.year);
    }
}

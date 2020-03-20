package org.commcare.android.tests.application;

import android.text.format.DateUtils;

import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.preferences.PrefValues;
import org.commcare.utils.PendingCalcs;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test auto-update triggering logic and update downloading retry logic once
 * the auto-update is triggered.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareTestApplication.class)
@RunWith(CommCareTestRunner.class)
public class PendingCalcTest {

    /**
     * Test the auto-update pending calculations, which have several edge
     * cases.
     */
    @Test
    public void testPendingCalc() {

        long checkedThreeDaysAgo = DateTime.now().minusDays(3).getMillis();
        Assert.assertTrue(PendingCalcs.isPending(checkedThreeDaysAgo, DateUtils.DAY_IN_MILLIS));

        long checkedThreeHoursAgo = DateTime.now().minusHours(3).getMillis();
        if (isSameDayAsNow(checkedThreeHoursAgo)) {
            Assert.assertFalse(PendingCalcs.isPending(checkedThreeHoursAgo, DateUtils.DAY_IN_MILLIS));
        } else {
            Assert.assertTrue(PendingCalcs.isPending(checkedThreeHoursAgo, DateUtils.DAY_IN_MILLIS));
        }

        // test different calendar day less than 24 hours ago trigger when
        // checking every day
        DateTime yesterdayNearMidnight = new DateTime();
        yesterdayNearMidnight =
                yesterdayNearMidnight.minusDays(1).withTimeAtStartOfDay().plusHours(23);
        DateTime now = new DateTime();
        long diff = yesterdayNearMidnight.minus(now.getMillis()).getMillis();
        Assert.assertTrue(diff < DateUtils.DAY_IN_MILLIS);
        Assert.assertTrue(PendingCalcs.isPending(yesterdayNearMidnight.getMillis(),
                DateUtils.DAY_IN_MILLIS));

        // test timeshift a couple of hours in the future, shouldn't be enough
        // to warrant a update trigger
        long hoursInTheFuture = DateTime.now().plusHours(2).getMillis();
        Assert.assertFalse(PendingCalcs.isPending(hoursInTheFuture, DateUtils.DAY_IN_MILLIS));

        // test timeshift where if we last checked more than one day in the
        // future then we trigger
        long daysLater = DateTime.now().plusDays(2).getMillis();
        Assert.assertTrue(PendingCalcs.isPending(daysLater, DateUtils.DAY_IN_MILLIS));

        long weekLater = DateTime.now().plusWeeks(1).getMillis();
        Assert.assertTrue(PendingCalcs.isPending(weekLater, DateUtils.DAY_IN_MILLIS));
    }

    private static boolean isSameDayAsNow(long checkTime) {
        return getDayOfWeek(checkTime) == Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
    }

    private static int getDayOfWeek(long time) {
        Calendar lastRestoreCalendar = Calendar.getInstance();
        lastRestoreCalendar.setTimeInMillis(time);
        return lastRestoreCalendar.get(Calendar.DAY_OF_WEEK);
    }
}

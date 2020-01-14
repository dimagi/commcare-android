package org.commcare.utils;

import org.joda.time.DateTime;
import org.joda.time.Hours;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

/**
 * Tests for various date calculations
 *
 * @author Clayton Sims (csims@dimagi.com)
 */
public class DateUtilTest {

    @Test
    public void testDateCalculations() {

        //Test Identity Calculations
        Date now = new Date();
        LocalDateTime ldnow = new LocalDateTime(now);
        DateTime dtnow = new DateTime(now);

        Assert.assertEquals("Dates are incompatible (Date -> LocalDate)", now.getTime(), ldnow.toDate().getTime());
        Assert.assertEquals("Dates are incompatible (Date -> DateTime)", now.getTime(), dtnow.toLocalDateTime().toDate().getTime());

        DateTime elevenPm = new DateTime(2020, 10, 10, 23, 00, 00);

        DateTime twoAmNd = elevenPm.plus(Hours.hours(4));

        DateTime elevenThirtyPmNd = new DateTime(2020, 10, 11, 23, 30, 00);

        DateTime elevenPmPlusThree = elevenPm.plus(Hours.hours(72));
        DateTime elevenPmPlusThreeDaysThirtyMinutes = elevenPm.plus(Hours.hours(72).plus(Minutes.minutes(30).toStandardHours()));
        DateTime elevenPmPlusThreeDaysOneHour = elevenPm.plus(Hours.hours(73));

        Assert.assertEquals(1, SyncDetailCalculations.getDaysBetweenJavaDatetimes(elevenPm.toDate(), twoAmNd.toDate()));

        Assert.assertEquals(0, SyncDetailCalculations.getDaysBetweenJavaDatetimes(twoAmNd.toDate(), elevenThirtyPmNd.toDate()));

        Assert.assertEquals(3, SyncDetailCalculations.getDaysBetweenJavaDatetimes(elevenPm.toDate(), elevenPmPlusThree.toDate()));

        Assert.assertEquals(3, SyncDetailCalculations.getDaysBetweenJavaDatetimes(elevenPm.toDate(), elevenPmPlusThreeDaysThirtyMinutes.toDate()));

        Assert.assertEquals(4, SyncDetailCalculations.getDaysBetweenJavaDatetimes(elevenPm.toDate(), elevenPmPlusThreeDaysOneHour.toDate()));


    }
}

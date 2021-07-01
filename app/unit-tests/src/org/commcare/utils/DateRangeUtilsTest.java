package org.commcare.utils;

import org.junit.Test;

import java.text.ParseException;

import androidx.core.util.Pair;

public class DateRangeUtilsTest {

    @Test
    public void testDateConversion() throws ParseException {
        String dateRange = "2020-02-15 to 2021-03-18";
        Pair<Long, Long> selection = DateRangeUtils.parseHumanReadableDate(dateRange);
        String startDate = DateRangeUtils.getDateFromTime(selection.first);
        String endDate = DateRangeUtils.getDateFromTime(selection.second);
        String humanReadableDateRange = DateRangeUtils.getHumanReadableDateRange(startDate, endDate);
        assert dateRange.contentEquals(humanReadableDateRange);
        assert DateRangeUtils.formatDateRangeAnswer(startDate, endDate).contentEquals("__range__2020-02-15__2021-03-18");
    }

}

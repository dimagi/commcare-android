package org.commcare.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.annotation.Nullable;

import androidx.core.util.Pair;

/**
 * Utility functions for DateRangePicker widget
 */
public class DateRangeUtils {

    // Changing this will require changing this format on ES end as well
    public static final String DATE_RANGE_ANSWER_PREFIX = "__range__";
    public static final String DATE_RANGE_ANSWER_DELIMITER = "__";
    public static final String DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER = " to ";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    /**
     * @param humanReadableDateRange human readable fomat for date range as 'startDate to endDate'
     * @return a Pair of start time and end time that can be supplied to MaterialDatePicker to set a date range,
     * @throws ParseException if the given humanReadableDateRange is not in 'yyyy-mm-dd to yyyy-mm-dd' format
     */
    @Nullable
    public static Pair<Long, Long> parseHumanReadableDate(String humanReadableDateRange) throws ParseException {
        if (humanReadableDateRange.contains(DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER)) {
            String[] humanReadableDateRangeSplit = humanReadableDateRange.split(DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER);
            if (humanReadableDateRangeSplit.length == 2) {
                SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
                Date startDate = sdf.parse(humanReadableDateRangeSplit[0]);
                Date endDate = sdf.parse(humanReadableDateRangeSplit[1]);
                return new Pair<>(startDate.getTime(), endDate.getTime());
            }
        }
        throw new ParseException("Argument " + humanReadableDateRange + " should be formatted as 'yyyy-mm-dd to yyyy-mm-dd'", 0);
    }

    /**
     * Formats __range__startDate__endDate as 'startDate to EndDate'
     *
     * @param dateRangeAnswer A date range value in form of '__range__startDate__endDate'
     * @return human readable format 'startDate to EndDate' for given dateRangeAnswer
     */
    public static String getHumanReadableDateRange(String dateRangeAnswer) {
        if (dateRangeAnswer != null && dateRangeAnswer.startsWith(DATE_RANGE_ANSWER_PREFIX)) {
            String[] dateRangeSplit = dateRangeAnswer.split(DATE_RANGE_ANSWER_DELIMITER);
            if (dateRangeSplit.length == 3) {
                return getHumanReadableDateRange(dateRangeSplit[2], dateRangeSplit[3]);
            }
        }
        return dateRangeAnswer;
    }


    // Formats as 'startDate to endDate'
    public static String getHumanReadableDateRange(String startDate, String endDate) {
        return startDate + DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER + endDate;
    }

    // Formats as '__range__startDate__endDate'
    public static String formatDateRangeAnswer(String startDate, String endDate) {
        return DATE_RANGE_ANSWER_PREFIX + startDate + DATE_RANGE_ANSWER_DELIMITER + endDate;
    }

    // Convers given time as yyyy-mm-dd
    public static String getDateFromTime(Long time) {
        return new SimpleDateFormat(DATE_FORMAT, Locale.US).format(new Date(time));
    }
}

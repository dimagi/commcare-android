package org.odk.collect.android.utilities;

/**
 * Simple Date class; holds month, year, and day of custom calendar system,
 * and milliseconds since the Java epoch for standard reference.
 *
 * @author Richard Lu
 */
public class UniversalDate {

    public static final long MILLIS_IN_DAY = 1000 * 60 * 60 * 24;

    public final int year;
    public final int month;
    public final int day;
    public final long millisFromJavaEpoch;

    public UniversalDate(int year, int month, int day, long millisFromJavaEpoch) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.millisFromJavaEpoch = millisFromJavaEpoch;
    }

}

package org.odk.collect.android.widgets;

import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;

import android.content.Context;
import android.content.res.Resources;
import android.util.SparseArray;

/**
 * Nepali Date Widget.
 * 
 * @author Richard Lu
 */
public class NepaliDateWidget extends AbstractUniversalDateWidget {
    
    private static final SparseArray<int[]> NEPALI_YEAR_MONTHS = new SparseArray<int[]>();
    
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2090;
    
    static {
        NEPALI_YEAR_MONTHS.put(2000, new int[] { 0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2001, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2002, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2003, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2004, new int[] { 0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2005, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2006, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2007, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2008, new int[] { 0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2009, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2010, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2011, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2012, new int[] { 0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2013, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2014, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2015, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2016, new int[] { 0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2017, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2018, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2019, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2020, new int[] { 0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2021, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2022, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2023, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2024, new int[] { 0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2025, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2026, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2027, new int[] { 0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2028, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2029, new int[] { 0, 31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2030, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2031, new int[] { 0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2032, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2033, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2034, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2035, new int[] { 0, 30, 32, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2036, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2037, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2038, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2039, new int[] { 0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2040, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2041, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2042, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2043, new int[] { 0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2044, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2045, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2046, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2047, new int[] { 0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2048, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2049, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2050, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2051, new int[] { 0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2052, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2053, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2054, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2055, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2056, new int[] { 0, 31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2057, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2058, new int[] { 0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2059, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2060, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2061, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2062, new int[] { 0, 30, 32, 31, 32, 31, 31, 29, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2063, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2064, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2065, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2066, new int[] { 0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2067, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2068, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2069, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2070, new int[] { 0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2071, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2072, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2073, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31 });
        NEPALI_YEAR_MONTHS.put(2074, new int[] { 0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2075, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2076, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2077, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31 });
        NEPALI_YEAR_MONTHS.put(2078, new int[] { 0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2079, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2080, new int[] { 0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2081, new int[] { 0, 31, 31, 32, 32, 31, 30, 30, 30, 29, 30, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2082, new int[] { 0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2083, new int[] { 0, 31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2084, new int[] { 0, 31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2085, new int[] { 0, 31, 32, 31, 32, 30, 31, 30, 30, 29, 30, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2086, new int[] { 0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2087, new int[] { 0, 31, 31, 32, 31, 31, 31, 30, 30, 29, 30, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2088, new int[] { 0, 30, 31, 32, 32, 30, 31, 30, 30, 29, 30, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2089, new int[] { 0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30 });
        NEPALI_YEAR_MONTHS.put(2090, new int[] { 0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30 });
    }

    private static long MIN_MILLIS_FROM_JAVA_EPOCH =
            -countDaysFromMinDay(2026, 9, 17) * AbstractUniversalDateWidget.MILLIS_IN_DAY;
    
    private static int countDaysFromMinDay(int toYear, int toMonth, int toDay) {
        if (toYear < MIN_YEAR || toYear > MAX_YEAR
                || toMonth < 1 || toMonth > 12
                || toDay < 1 || toDay > NEPALI_YEAR_MONTHS.get(toYear)[toMonth]) {
            throw new RuntimeException("Date out of bounds");
        }
        
        int daysFromMinDay = -1;
        
        for (int year = MIN_YEAR; year <= toYear; year++) {
            int[] monthsInYear = NEPALI_YEAR_MONTHS.get(year);
            
            for (int month = 1; month <= 12; month++) {
                int daysInMonth = monthsInYear[month];
                
                for (int day = 1; day <= daysInMonth; day++) {
                    daysFromMinDay++;
                    
                    if (year == toYear
                            && month == toMonth
                            && day == toDay) {
                        return daysFromMinDay;
                    }
                }
            }
        }
        
        throw new RuntimeException("Calculation error!");
    }
    
    public NepaliDateWidget(Context context, FormEntryPrompt prompt) {
    	super(context, prompt);
    }
    
    @Override
    protected String[] getMonthsArray() {
        Resources res = getResources();
        // load the months - will automatically get correct strings for current phone locale
        return res.getStringArray(R.array.nepali_months);
    }
    
    @Override
    protected UniversalDate decrementMonth(long millisFromJavaEpoch) {
    	UniversalDate origDate = fromMillis(millisFromJavaEpoch);
    	int year = origDate.year;
    	int month = origDate.month;
    	int day = origDate.day;
    	
    	month--;
    	if (month < 1) {
    	    month = 12;
    	    year--;
    	}
    	
    	if (year < MIN_YEAR) {
    	    year = MAX_YEAR;
    	}
    	
    	if (day > NEPALI_YEAR_MONTHS.get(year)[month]) {
    	    day = NEPALI_YEAR_MONTHS.get(year)[month];
    	}
    	
    	return new UniversalDate(
    	        year,
    	        month,
    	        day,
    	        toMillisFromJavaEpoch(year, month, day)
        );
    }
    
    @Override
    protected UniversalDate decrementYear(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        int year = origDate.year;
        int month = origDate.month;
        int day = origDate.day;

        year--;
        if (year < MIN_YEAR) {
            year = MAX_YEAR;
        }
        
        if (day > NEPALI_YEAR_MONTHS.get(year)[month]) {
            day = NEPALI_YEAR_MONTHS.get(year)[month];
        }
        
        return new UniversalDate(
                year,
                month,
                day,
                toMillisFromJavaEpoch(year, month, day)
        );
    }
    
    @Override
    protected UniversalDate fromMillis(long millisFromJavaEpoch) {
        long millisFromMinDay = millisFromJavaEpoch - MIN_MILLIS_FROM_JAVA_EPOCH;
        long daysFromMinDay = millisFromMinDay / AbstractUniversalDateWidget.MILLIS_IN_DAY;
        
        int days = -1;
        
        for (int year = MIN_YEAR; year <= MAX_YEAR; year++) {
            int[] monthsInYear = NEPALI_YEAR_MONTHS.get(year);
            
            for (int month = 1; month <= 12; month++) {
                int daysInMonth = monthsInYear[month];
                
                for (int day = 1; day <= daysInMonth; day++) {
                    days++;
                    
                    if (days == daysFromMinDay) {
                        return new UniversalDate(
                                year,
                                month,
                                day,
                                millisFromJavaEpoch
                        );
                    }
                }
            }
        }
        
        throw new RuntimeException("Date out of bounds");
    }
    
    @Override
    protected UniversalDate incrementMonth(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        int year = origDate.year;
        int month = origDate.month;
        int day = origDate.day;
        
        month++;
        if (month > 12) {
            month = 1;
            year++;
        }
        
        if (year > MAX_YEAR) {
            year = MIN_YEAR;
        }
        
        if (day > NEPALI_YEAR_MONTHS.get(year)[month]) {
            day = NEPALI_YEAR_MONTHS.get(year)[month];
        }
        
        return new UniversalDate(
                year,
                month,
                day,
                toMillisFromJavaEpoch(year, month, day)
        );
    }
    
    @Override
    protected UniversalDate incrementYear(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        int year = origDate.year;
        int month = origDate.month;
        int day = origDate.day;
        
        year++;
        if (year > MAX_YEAR) {
            year = MIN_YEAR;
        }
        
        if (day > NEPALI_YEAR_MONTHS.get(year)[month]) {
            day = NEPALI_YEAR_MONTHS.get(year)[month];
        }
        
        return new UniversalDate(
                year,
                month,
                day,
                toMillisFromJavaEpoch(year, month, day)
        );
    }
    
    @Override
    protected long toMillisFromJavaEpoch(int year, int month, int day) {
        int daysFromMinDay = countDaysFromMinDay(year, month, day);
        long millisFromMinDay = daysFromMinDay * AbstractUniversalDateWidget.MILLIS_IN_DAY;
        return millisFromMinDay + MIN_MILLIS_FROM_JAVA_EPOCH;
    }
}

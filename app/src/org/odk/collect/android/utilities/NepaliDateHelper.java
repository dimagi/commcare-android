package org.odk.collect.android.utilities;

import java.util.Date;

import org.commcare.dalvik.R;

import android.content.Context;

/**
 * Helper to convert a Gregorian Date to a Nepali date, formatted as a 'd MMMM yyyy' string.
 * 
 * @author Richard Lu
 */
public class NepaliDateHelper {

    public static String ConvertToNepali(Context context, Date date) {
        String[] months = context.getResources().getStringArray(R.array.nepali_months);
        
        UniversalDate dateUniv = NepaliDateUtilities.fromMillis(date.getTime());
        
        return dateUniv.day + " " + months[dateUniv.month - 1] + " " + dateUniv.year;
    }
    
}

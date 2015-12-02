package org.odk.collect.android.utilities;

import android.content.Context;

import org.commcare.dalvik.R;
import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.chrono.EthiopicChronology;
import org.joda.time.chrono.GregorianChronology;

import java.util.Calendar;
import java.util.Date;

/**
 * Ethiopian Date Helper.
 * 
 * @author Alex Little (alex@alexlittle.net)
 */

public class EthiopianDateHelper {

    private static String ConvertToEthiopian(Context context, int gregorianYear, int gregorianMonth, int gregorianDay){
        Chronology chron_eth = EthiopicChronology.getInstance();
        Chronology chron_greg = GregorianChronology.getInstance();
        DateTime jodaDateTime = new DateTime(gregorianYear, gregorianMonth, gregorianDay, 0, 0, 0, chron_greg);
        DateTime dtEthiopic = jodaDateTime.withChronology(chron_eth);
        String[] monthsArray = context.getResources().getStringArray(R.array.ethiopian_months);

        return dtEthiopic.getDayOfMonth() + " "
                + monthsArray[dtEthiopic.getMonthOfYear() - 1] + " "
                + dtEthiopic.getYear();
    }

    public static Object ConvertToEthiopian(Context context, Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return ConvertToEthiopian(context, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}

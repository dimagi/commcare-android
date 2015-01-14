package org.odk.collect.android.widgets;

import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.chrono.EthiopicChronology;

import android.content.Context;
import android.content.res.Resources;

/**
 * Ethiopian Date Widget.
 * 
 * @author Alex Little (alex@alexlittle.net), Richard Lu
 */
public class EthiopianDateWidget extends AbstractUniversalDateWidget {
    
    public EthiopianDateWidget(Context context, FormEntryPrompt prompt) {
    	super(context, prompt);
    }
    
    private static Chronology chron_eth = EthiopicChronology.getInstance();
    
    private UniversalDate constructUniversalDate(DateTime dt) {
    	return new UniversalDate(
    			dt.getYear(),
    			dt.getMonthOfYear(),
    			dt.getDayOfMonth(),
    			dt.getMillis()
    	);
    }
    
    @Override
    protected UniversalDate decrementMonth(long millisFromJavaEpoch) {
    	DateTime dt = new DateTime(millisFromJavaEpoch)
    		.withChronology(chron_eth)
    		.minusMonths(1);
    	return constructUniversalDate(dt);
    }
    
    @Override
    protected UniversalDate decrementYear(long millisFromJavaEpoch) {
    	DateTime dt = new DateTime(millisFromJavaEpoch)
    		.withChronology(chron_eth)
    		.minusYears(1);
    	return constructUniversalDate(dt);
    }
    
    @Override
    protected UniversalDate fromMillis(long millisFromJavaEpoch) {
    	DateTime dt = new DateTime(millisFromJavaEpoch)
    		.withChronology(chron_eth);
    	return constructUniversalDate(dt);
    }
    
    @Override
    protected String[] getMonthsArray() {
        Resources res = getResources();
        // load the months - will automatically get correct strings for current phone locale
        return res.getStringArray(R.array.ethiopian_months);
    }
    
    @Override
    protected UniversalDate incrementMonth(long millisFromJavaEpoch) {
    	DateTime dt = new DateTime(millisFromJavaEpoch)
    		.withChronology(chron_eth)
    		.plusMonths(1);
    	return constructUniversalDate(dt);
    }
    
    @Override
    protected UniversalDate incrementYear(long millisFromJavaEpoch) {
    	DateTime dt = new DateTime(millisFromJavaEpoch)
    		.withChronology(chron_eth)
    		.plusYears(1);
    	return constructUniversalDate(dt);
    }
    
    @Override
    protected long toMillisFromJavaEpoch(int year, int month, int day) {
    	DateTime dt = new DateTime(chron_eth)
    		.withYear(year)
    		.withMonthOfYear(month)
    		.withDayOfMonth(day);
    	return dt.getMillis();
    }
}

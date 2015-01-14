package org.odk.collect.android.widgets;

import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;

import android.content.Context;
import android.content.res.Resources;

/**
 * Nepali Date Widget.
 * 
 * @author Richard Lu
 */
public class NepaliDateWidget extends AbstractUniversalDateWidget {
    
    public NepaliDateWidget(Context context, FormEntryPrompt prompt) {
    	super(context, prompt);
    }
    
    @Override
    protected String[] getMonthsArray() {
        Resources res = getResources();
        // load the months - will automatically get correct strings for current phone locale
        return res.getStringArray(R.array.nepali_months);
    }
    
    private UniversalDate dummy = new UniversalDate(0, 0, 0, 0);
    
    @Override
    protected UniversalDate decrementMonth(long millisFromJavaEpoch) {
    	// TODO Auto-generated method stub
    	return dummy;
    }
    
    @Override
    protected UniversalDate decrementYear(long millisFromJavaEpoch) {
    	// TODO Auto-generated method stub
    	return dummy;
    }
    
    @Override
    protected UniversalDate fromMillis(long millisFromJavaEpoch) {
    	// TODO Auto-generated method stub
    	return dummy;
    }
    
    @Override
    protected UniversalDate incrementMonth(long millisFromJavaEpoch) {
    	// TODO Auto-generated method stub
    	return dummy;
    }
    
    @Override
    protected UniversalDate incrementYear(long millisFromJavaEpoch) {
    	// TODO Auto-generated method stub
    	return dummy;
    }
    
    @Override
    protected long toMillisFromJavaEpoch(int year, int month, int day) {
    	// TODO Auto-generated method stub
    	return 0;
    }
}

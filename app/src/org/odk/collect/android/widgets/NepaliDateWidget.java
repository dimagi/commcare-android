package org.odk.collect.android.widgets;

import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.utilities.NepaliDateUtilities;
import org.odk.collect.android.utilities.UniversalDate;

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

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.AbstractUniversalDateWidget#decrementMonth(long)
     */
    @Override
    protected UniversalDate decrementMonth(long millisFromJavaEpoch) {
    	UniversalDate origDate = fromMillis(millisFromJavaEpoch);
    	return NepaliDateUtilities.decrementMonth(origDate);
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.AbstractUniversalDateWidget#decrementYear(long)
     */
    @Override
    protected UniversalDate decrementYear(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliDateUtilities.decrementYear(origDate);
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.AbstractUniversalDateWidget#fromMillis(long)
     */
    @Override
    protected UniversalDate fromMillis(long millisFromJavaEpoch) {
        return NepaliDateUtilities.fromMillis(millisFromJavaEpoch);
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.AbstractUniversalDateWidget#getMonthsArray()
     */
    @Override
    protected String[] getMonthsArray() {
        Resources res = getResources();
        // load the months - will automatically get correct strings for current phone locale
        return res.getStringArray(R.array.nepali_months);
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.AbstractUniversalDateWidget#incrementMonth(long)
     */
    @Override
    protected UniversalDate incrementMonth(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliDateUtilities.incrementMonth(origDate);
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.AbstractUniversalDateWidget#incrementYear(long)
     */
    @Override
    protected UniversalDate incrementYear(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliDateUtilities.incrementYear(origDate);
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.AbstractUniversalDateWidget#toMillisFromJavaEpoch(int,int,int,long)
     */
    @Override
    protected long toMillisFromJavaEpoch(int year, int month, int day, long millisOffset) {
        return NepaliDateUtilities.toMillisFromJavaEpoch(year, month, day, millisOffset);
    }
}

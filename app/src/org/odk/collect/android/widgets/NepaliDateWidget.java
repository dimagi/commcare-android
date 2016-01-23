package org.odk.collect.android.widgets;

import android.content.Context;
import android.content.res.Resources;

import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.utilities.NepaliDateUtilities;
import org.odk.collect.android.utilities.UniversalDate;

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
    protected UniversalDate decrementMonth(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliDateUtilities.decrementMonth(origDate);
    }

    @Override
    protected UniversalDate decrementYear(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliDateUtilities.decrementYear(origDate);
    }

    @Override
    protected UniversalDate fromMillis(long millisFromJavaEpoch) {
        return NepaliDateUtilities.fromMillis(millisFromJavaEpoch);
    }

    @Override
    protected String[] getMonthsArray() {
        Resources res = getResources();
        // load the months - will automatically get correct strings for current phone locale
        return res.getStringArray(R.array.nepali_months);
    }

    @Override
    protected UniversalDate incrementMonth(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliDateUtilities.incrementMonth(origDate);
    }

    @Override
    protected UniversalDate incrementYear(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliDateUtilities.incrementYear(origDate);
    }

    @Override
    protected long toMillisFromJavaEpoch(int year, int month, int day, long millisOffset) {
        return NepaliDateUtilities.toMillisFromJavaEpoch(year, month, day, millisOffset);
    }
}

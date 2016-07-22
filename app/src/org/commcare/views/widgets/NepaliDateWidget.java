package org.commcare.views.widgets;

import android.content.Context;
import org.javarosa.core.services.locale.Localization;
import org.commcare.utils.NepaliDateUtilities;
import org.commcare.utils.UniversalDate;
import org.javarosa.form.api.FormEntryPrompt;

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
        return Localization.getArray("nepali.months.list");
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

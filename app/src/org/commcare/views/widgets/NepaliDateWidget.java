package org.commcare.views.widgets;

import android.content.Context;

import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xform.util.CalendarUtils;
import org.javarosa.xform.util.NepaliCalendarUtils;
import org.javarosa.xform.util.UniversalDate;

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
        return NepaliCalendarUtils.decrementMonth(origDate);
    }

    @Override
    protected UniversalDate decrementYear(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliCalendarUtils.decrementYear(origDate);
    }

    @Override
    protected UniversalDate fromMillis(long millisFromJavaEpoch) {
        return NepaliCalendarUtils.fromMillis(millisFromJavaEpoch);
    }

    @Override
    protected String[] getMonthsArray() {
        return CalendarUtils.getMonthsArray("nepali_months");
    }

    @Override
    protected UniversalDate incrementMonth(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliCalendarUtils.incrementMonth(origDate);
    }

    @Override
    protected UniversalDate incrementYear(long millisFromJavaEpoch) {
        UniversalDate origDate = fromMillis(millisFromJavaEpoch);
        return NepaliCalendarUtils.incrementYear(origDate);
    }

    @Override
    protected long toMillisFromJavaEpoch(int year, int month, int day) {
        return NepaliCalendarUtils.toMillisFromJavaEpoch(year, month, day);
    }
}

package org.commcare.views.widgets;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;

/**
 * Created by Saumya on 5/27/2016.
 * Uses the increment/decrement button structure to input Gregorian dates
 */
public class GregorianWidget extends AbstractUniversalDateWidget {

    public GregorianWidget(Context context, FormEntryPrompt prompt){
        super(context, prompt); //Adds all the stuff for abstract date widget
        View gregDate = findViewById(R.id.dateGregorian);
        //removeView(gregDate);//remove helper date because it's unnecessary
        gregDate.setVisibility(View.GONE);

        //TODO: Add key input functionality when text views are clicked on
    }

    @Override
    protected UniversalDate decrementMonth(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).minusMonths(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected UniversalDate decrementYear(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).minusYears(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected UniversalDate fromMillis(long millisFromJavaEpoch) {
        return constructUniversalDate(new DateTime(millisFromJavaEpoch));
    }

    @Override
    protected String[] getMonthsArray() {
        return new String[]{"January", "February", "March", "April", "May", "June", "July",
                            "August","September","October","November","December"};
    }

    @Override
    protected UniversalDate incrementMonth(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).plusMonths(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected UniversalDate incrementYear(long millisFromJavaEpoch) {
        DateTime dt = new DateTime(millisFromJavaEpoch).plusYears(1);
        return constructUniversalDate(dt);
    }

    @Override
    protected long toMillisFromJavaEpoch(int year, int month, int day, long millisOffset) {
        DateTime dt = new DateTime()
                .withYear(year)
                .withMonthOfYear(month)
                .withDayOfMonth(day)
                .withMillisOfDay((int)millisOffset);
        return dt.getMillis();
    }

    private UniversalDate constructUniversalDate(DateTime dt) {
        return new UniversalDate(
                dt.getYear(),
                dt.getMonthOfYear(),
                dt.getDayOfMonth(),
                dt.getMillis()
        );
    }
}

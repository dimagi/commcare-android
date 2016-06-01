package org.commcare.views.widgets;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;

import java.util.Calendar;

/**
 * Created by Saumya on 5/27/2016.
 * Uses the increment/decrement button structure to input Gregorian dates
 */
public class GregorianWidget extends AbstractUniversalDateWidget {

    private EditText dayTxt;
    private EditText monthTxt;
    private EditText yearTxt;
    private Calendar myCal;

    private String[] myMonths;

    public GregorianWidget(Context context, FormEntryPrompt prompt){
        super(context, prompt); //Adds all the stuff for abstract date widget
        myCal = Calendar.getInstance();


        //TODO: Validate input from text fields: Use a TextWatcher for this, use myCal to validate days in month
        //TODO: Update month array pointer based on text entered..check to see how super does this though
    }

    @Override
    protected void initText(){
        dayTxt = (EditText)findViewById(R.id.daytxt);
        monthTxt = (EditText)findViewById(R.id.monthtxt);
        yearTxt = (EditText)findViewById(R.id.yeartxt);
    }

    @Override
    protected void inflateView(Context context){
        LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View vv = vi.inflate(R.layout.gregorian_date_widget, null);
        addView(vv);
    }

    @Override
    protected void updateDateDisplay(long millisFromJavaEpoch) {
        UniversalDate dateUniv = fromMillis(millisFromJavaEpoch);
        dayTxt.setText(String.format("%02d", dateUniv.day));
        monthTxt.setText(monthsArray[dateUniv.month - 1]);
        monthArrayPointer = dateUniv.month - 1;
        yearTxt.setText(String.format("%04d", dateUniv.year));

        if(myCal == null) {
            myCal = Calendar.getInstance();
        }

        myCal.setTimeInMillis(millisFromJavaEpoch);
    }

    @Override
    protected long getCurrentMillis() {
        int day = Integer.parseInt(dayTxt.getText().toString());
        int month = monthArrayPointer + 1;
        int year = Integer.parseInt(yearTxt.getText().toString());
        return toMillisFromJavaEpoch(year, month, day, millisOfDayOffset);
    }

    @Override
    protected void updateGregorianDateHelperDisplay(){}

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
        if(myMonths == null){
            myMonths = new String[]{"January", "February", "March", "April", "May", "June", "July",
                    "August","September","October","November","December"};
        }
        return myMonths;
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

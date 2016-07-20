package org.commcare.views.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Gregorian date widget that has a wheel for choosing month instead of text entry
 */
public class SpinnerGregorianWidget extends GregorianDateWidget {

    private DatePicker datepicker;

    public SpinnerGregorianWidget(Context context, FormEntryPrompt prompt, boolean closeButton, String calendarType){
        super(context, prompt, closeButton, calendarType);
    }

    @Override
    protected void setupMonthComponents(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            datepicker.setCalendarViewShown(false);
        }

        datepicker.findViewById(Resources.getSystem().getIdentifier("day", "id", "android")).setVisibility(View.GONE);
        datepicker.findViewById(Resources.getSystem().getIdentifier("year", "id", "android")).setVisibility(View.GONE);

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        datepicker.init(year, month, day, new DatePicker.OnDateChangedListener() {
            @Override
            public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                calendar.set(year, monthOfYear, dayOfMonth);
                updateDateDisplay(calendar.getTimeInMillis());
            }
        });
    }

    @Override
    protected void inflateView(Context context){
        LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        gregorianView = (LinearLayout) vi.inflate(R.layout.spinner_gregorian_widget, null);
        addView(gregorianView);
        datepicker = (DatePicker) gregorianView.findViewById(R.id.month_picker);
    }

    @Override
    protected void updateDateDisplay(long millisFromJavaEpoch) {
        UniversalDate dateUniv = fromMillis(millisFromJavaEpoch);
        monthArrayPointer = dateUniv.month - 1;
        dayText.setText(String.format(DAYFORMAT, dateUniv.day));
        yearText.setText(String.format(YEARFORMAT, dateUniv.year));
        calendar.setTimeInMillis(millisFromJavaEpoch);

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        datepicker.init(year, month, day, new DatePicker.OnDateChangedListener() {
            @Override
            public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                calendar.set(year, monthOfYear, dayOfMonth);
                updateDateDisplay(calendar.getTimeInMillis());
            }
        });

        dayOfWeek.setText(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()));
    }

    @Override
    protected void autoFillEmptyTextFields() {
        if(dayText.getText().toString().isEmpty()){
            dayText.setText(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
        }

        if(yearText.getText().toString().isEmpty()){
            yearText.setText(String.valueOf(calendar.get(Calendar.YEAR)));
        }
    }

    @Override
    protected void validateTextOnButtonPress() {
        String dayTextValue = dayText.getText().toString();
        int num = Integer.parseInt(dayTextValue);
        int monthMax = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        if (num <= monthMax && num >= 1) {
            calendar.set(Calendar.DAY_OF_MONTH, num);
        }else{
            dayText.setText(String.valueOf(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }

        calendar.set(Calendar.MONTH, datepicker.getMonth());

        String yearTextValue = yearText.getText().toString();
        calendar.set(Calendar.YEAR, Integer.parseInt(yearTextValue));
    }

    @Override
    public IAnswerData getAnswer(){
        setFocus(getContext());
        String day = dayText.getText().toString();
        String year = yearText.getText().toString();

        //All fields are empty - Like submitting a blank date
        if(day.isEmpty() && year.isEmpty()){
            return null;
        }

        //Some but not all fields are empty - Error
        if(day.isEmpty() || year.isEmpty()){
            return new InvalidData(Localization.get("empty.fields"), new DateData(calendar.getTime()));
        }

        //Invalid year (too low)
        if(Integer.parseInt(year) < MINYEAR){
            return new InvalidData(Localization.get("low.year"), new DateData(calendar.getTime()));
        }

        //Invalid day (too high)
        if(Integer.parseInt(day) > calendar.getActualMaximum(Calendar.DAY_OF_MONTH)){
            return new InvalidData(Localization.get("high.date") + " " + String.valueOf(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)), new DateData(calendar.getTime()));
        }

        //Invalid day (too high)
        if(Integer.parseInt(day)< 1){
            return new InvalidData(Localization.get("low.date"), new DateData(calendar.getTime()));
        }

        //Invalid year (too high)
        if(Integer.parseInt(year) > maxYear){
            return new InvalidData(Localization.get("high.year") + " " + String.valueOf(maxYear), new DateData(calendar.getTime()));
        }

        Date date = getDateAsGregorian();
        return new DateData(date);
    }

    @Override
    protected void clearAll(){
        dayText.setText("");
        yearText.setText("");
        setFocus(getContext());
    }

    @Override
    public void setFocus(Context context) {
        dayText.setCursorVisible(false);
        yearText.setCursorVisible(false);
    }

}

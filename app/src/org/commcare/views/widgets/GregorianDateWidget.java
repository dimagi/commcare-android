package org.commcare.views.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.DateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Saumya on 5/27/2016.
 * A widget that accepts Gregorian dates using logic and GUI that are similar to the Nepali and Ethiopian widgets
 */
public class GregorianDateWidget extends AbstractUniversalDateWidget {

    private EditText dayText;
    private AutoCompleteTextView monthText;
    private EditText yearText;
    private TextView dayOfWeek;
    protected Calendar calendar;
    private List<String> monthList;
    private final int MINYEAR = 1900;
    private final String DAYFORMAT = "%02d";
    private final String YEARFORMAT = "%04d";
    private int maxYear;

    protected LinearLayout gregorianView;

    public GregorianDateWidget(Context context, FormEntryPrompt prompt){
        super(context, prompt);
        maxYear = calendar.get(Calendar.YEAR) + 1;
        ImageButton clearAll = (ImageButton) findViewById(R.id.clear_all);
        clearAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAll();
            }
        });
    }

    @Override
    protected void initText(){
        dayOfWeek = (TextView) findViewById(R.id.greg_day_of_week);
        dayText = (EditText)findViewById(R.id.day_txt_field);
        yearText = (EditText)findViewById(R.id.year_txt_field);
        monthText = (AutoCompleteTextView) findViewById(R.id.month_txt_field);

        MonthAdapter monthAdapter = new MonthAdapter(getContext(), monthList);
        monthText.setAdapter(monthAdapter);
    }

    @Override
    protected void inflateView(Context context){
        LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        gregorianView = (LinearLayout) vi.inflate(R.layout.gregorian_date_widget, null);
        addView(gregorianView);
    }

    @Override
    protected void updateDateDisplay(long millisFromJavaEpoch) {
        UniversalDate dateUniv = fromMillis(millisFromJavaEpoch);
        monthArrayPointer = dateUniv.month - 1;
        dayText.setText(String.format(DAYFORMAT, dateUniv.day));
        monthText.setText(monthsArray[monthArrayPointer]);
        yearText.setText(String.format(YEARFORMAT, dateUniv.year));
        calendar.setTimeInMillis(millisFromJavaEpoch);
        dayOfWeek.setText(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()));
    }

    @Override
    protected long getCurrentMillis() {

        autoFillEmptyTextFields();
        validateTextOnButtonPress();

        int day = Integer.parseInt(dayText.getText().toString());
        int month = monthArrayPointer + 1;
        int year = Integer.parseInt(yearText.getText().toString());

        return toMillisFromJavaEpoch(year, month, day, millisOfDayOffset);
    }

    private void autoFillEmptyTextFields() {
        if(dayText.getText().toString().isEmpty()){
            dayText.setText(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
        }

        if(monthText.getText().toString().isEmpty()){
            monthText.setText(monthsArray[monthArrayPointer]);
        }

        if(yearText.getText().toString().isEmpty()){
            yearText.setText(String.valueOf(calendar.get(Calendar.YEAR)));
        }
    }

    //Checks if all text fields contain valid values, corrects fields with invalid values, updates calendar based on text fields
    private void validateTextOnButtonPress(){
        String dayTextValue = dayText.getText().toString();
        int num = Integer.parseInt(dayTextValue);
        int monthMax = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        if (num <= monthMax && num >= 1) {
            calendar.set(Calendar.DAY_OF_MONTH, num);
        }else{
            dayText.setText(String.valueOf(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }

        String monthTextValue = monthText.getText().toString();
        if(monthList.contains(monthTextValue)){
            monthArrayPointer = monthList.indexOf(monthTextValue);
            calendar.set(Calendar.MONTH, monthArrayPointer);
        }
        monthText.clearFocus();

        String yearTextValue = yearText.getText().toString();
        calendar.set(Calendar.YEAR, Integer.parseInt(yearTextValue));
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
    protected String[] getMonthsArray() {
        calendar = Calendar.getInstance();

        String [] monthNames = new String[12];
        final Map<String, Integer> monthMap = calendar.getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        monthList = new ArrayList<>(monthMap.keySet());
            Collections.sort(monthList, new Comparator<String>(){
                @Override
                public int compare(String a, String b){
                    return monthMap.get(a) - monthMap.get(b);
                }
            });

        monthNames = monthList.toArray(monthNames);
        return monthNames;
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

    @Override
    protected void updateGregorianDateHelperDisplay(){}

    //Needs all of these checks in order to prevent forward navigation if input is invalid
    @Override
    public IAnswerData getAnswer(){
        setFocus(getContext());

        String month = monthText.getText().toString();
        String day = dayText.getText().toString();
        String year = yearText.getText().toString();

        //Empty text fields
        if(month.isEmpty() || day.isEmpty() || year.isEmpty()){
            return null;
        }
        //Invalid year (too low)
        if(Integer.parseInt(year) < MINYEAR){
            return new InvalidData(Localization.get("low.year"), new DateData(calendar.getTime()));
        }
        //Invalid month
        if(!monthList.contains(month)){
            return new InvalidData(Localization.get("invalid.month"), new DateData(calendar.getTime()));
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
            return new InvalidData(Localization.get("high.year") + String.valueOf(maxYear), new DateData(calendar.getTime()));
        }
        return super.getAnswer();
    }

    private UniversalDate constructUniversalDate(DateTime dt) {
        return new UniversalDate(
                dt.getYear(),
                dt.getMonthOfYear(),
                dt.getDayOfMonth(),
                dt.getMillis()
        );
    }

    private void clearAll(){
        dayText.setText("");
        monthText.setText("");
        yearText.setText("");
        super.setFocus(getContext());
    }

    protected void refreshDisplay(){
        updateDateDisplay(calendar.getTimeInMillis());
    }

    private class MonthAdapter extends ArrayAdapter<String>{

        private LayoutInflater mInflater;

        public MonthAdapter(Context context,List<String> months){
            super(context, R.layout.calendar_date, months);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent){
            if(view == null){
                view = mInflater.inflate(R.layout.calendar_date, null);
            }

            String month = getItem(position);

            TextView text = (TextView) view;
            text.setHeight(120);
            text.setText(month);
            return text;
        }
    }
}
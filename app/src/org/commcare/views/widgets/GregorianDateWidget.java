package org.commcare.views.widgets;

import android.content.Context;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
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

/**
 * Month Type: text, spinner, list
 * Cancel Button type: true, false
 * Calendar type: arrow, calspinner, callist
 *
 * Default" [text, true, arrow]
 *
 * Given spinner and list, instantiate different subclasses of prototype1 from factory
 * Given cancel button type, pass true/false as a constructor param to the prototype
 * Given calendar type, pass it as a constructor param to the prototype and instantiate a different subclass of calendarfragment
 *
 * TODO: Change setAnswer() method to reload an invalid widget with its actual state instead of defaulting to the most recently entered valid date
 *
 */
public class GregorianDateWidget extends AbstractUniversalDateWidget implements CalendarFragment.CalendarCloseListener {

    protected EditText dayText;
    private AutoCompleteTextView monthText;
    protected EditText yearText;
    protected TextView dayOfWeek;
    protected Calendar calendar;
    protected List<String> monthList;
    protected final int MINYEAR = 1900;
    protected final String DAYFORMAT = "%02d";
    protected final String YEARFORMAT = "%04d";
    protected int maxYear;
    protected long todaysDateInMillis;

    private CalendarFragment myCalendarFragment;
    private ImageButton openCalButton;
    private FragmentManager fm;
    private long timeBeforeCalendarOpened;

    protected LinearLayout gregorianView;

    public GregorianDateWidget(Context context, FormEntryPrompt prompt, boolean closeButton, String calendarType){
        super(context, prompt);
        maxYear = calendar.get(Calendar.YEAR) + 1;
        todaysDateInMillis = calendar.getTimeInMillis();
        ImageButton clearAll = (ImageButton) findViewById(R.id.clear_all);

        if(closeButton){
            clearAll.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearAll();
                }
            });
        }else{
            clearAll.setVisibility(View.GONE);
        }

        fm = ((FragmentActivity) getContext()).getSupportFragmentManager();

        if(calendarType.equals("calspinner")){
            myCalendarFragment = new SpinnerCalendarFragment();
        }
        else if(calendarType.equals("callist")){
            myCalendarFragment = new ScrollingCalendarFragment();
        }
        else{
            myCalendarFragment = new CalendarFragment();
        }

        myCalendarFragment.setCalendar(calendar, todaysDateInMillis);

        openCalButton = (ImageButton) findViewById(R.id.open_calendar_bottom);
        openCalButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });

        myCalendarFragment.setListener(this);
        myCalendarFragment.setCancelable(false);
    }

    @Override
    protected void initText(){
        dayOfWeek = (TextView) findViewById(R.id.greg_day_of_week);
        dayText = (EditText)findViewById(R.id.day_txt_field);
        yearText = (EditText)findViewById(R.id.year_txt_field);

        dayText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dayText.clearFocus();
                dayText.requestFocus();
            }
        });

        yearText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                yearText.clearFocus();
                yearText.requestFocus();
            }
        });

        setupMonthComponents();

    }

    protected void setupMonthComponents(){
        monthText = (AutoCompleteTextView) findViewById(R.id.month_txt_field);
        monthText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                monthText.clearFocus();
                monthText.requestFocus();
            }
        });

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

    protected void autoFillEmptyTextFields() {
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
    protected void validateTextOnButtonPress(){
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

        //All fields are empty - Like submitting a blank date
        if(month.isEmpty() && day.isEmpty() && year.isEmpty()){
            return null;
        }

        //Some but not all fields are empty - Error
        if(month.isEmpty() || day.isEmpty() || year.isEmpty()){
            return new InvalidData(Localization.get("empty.fields"), new DateData(calendar.getTime()));
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
            return new InvalidData(Localization.get("high.year") + " " + String.valueOf(maxYear), new DateData(calendar.getTime()));
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

    protected void clearAll(){
        dayText.setText("");
        monthText.setText("");
        yearText.setText("");
        setFocus(getContext());
    }

    @Override
    public void setFocus(Context context) {
        super.setFocus(context);
        dayText.setCursorVisible(false);
        monthText.setCursorVisible(false);
        yearText.setCursorVisible(false);

    }

    protected void refreshDisplay(){
        updateDateDisplay(calendar.getTimeInMillis());
    }

    private class MonthAdapter extends ArrayAdapter<String>{

        private LayoutInflater mInflater;

        public MonthAdapter(Context context, List<String> months){
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

    protected void openCalendar() {
        Log.d("MONTH", String.valueOf(calendar.get(Calendar.MONTH)));
        setFocus(getContext());
        timeBeforeCalendarOpened = calendar.getTimeInMillis();
        myCalendarFragment.show(fm, "Calendar Popup");
    }

    @Override
    public void onCalendarClose() {
        refreshDisplay();
        setFocus(getContext());
    }

    @Override
    public void onCalendarCancel(){
        calendar.setTimeInMillis(timeBeforeCalendarOpened);
        onCalendarClose();
    }

    @Override
    protected void setAnswer(){
        if(!(mPrompt.getAnswerValue() instanceof InvalidData)){
            super.setAnswer();
        }
    }
}
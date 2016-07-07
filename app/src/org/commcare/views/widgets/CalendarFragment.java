package org.commcare.views.widgets;

import android.content.DialogInterface;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.javarosa.core.model.data.DateData;
import org.commcare.dalvik.R;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Saumya on 5/29/2016.
 * DialogFragment for a popup calendar icon
 * Uses support library for compatibility with pre-honeycomb devices
 * Layout and logic inspired by Ahmed Al-Amir at https://www.toptal.com/android/android-customization-how-to-build-a-ui-component-that-does-what-you-want
 */
public class CalendarFragment extends android.support.v4.app.DialogFragment {

    private GridView myGrid;
    private ImageButton decMonth;
    private ImageButton incMonth;
    private ImageButton decYear;
    private ImageButton incYear;
    private TextView myMonth;
    private TextView myYear;
    private Calendar calendar;
    private LinearLayout myLayout;
    private CalendarCloseListener myListener;

    private static final int DAYSINWEEK = 7;
    private static final String TIME = "TIME";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        myLayout = (LinearLayout) inflater.inflate(R.layout.calendar_widget, container);

        if(savedInstanceState != null && savedInstanceState.containsKey(TIME)){
            calendar = Calendar.getInstance();
            calendar.setTimeInMillis(savedInstanceState.getLong(TIME));
        }

        initDisplay();
        initWeekDays();
        initOnClick();
        refresh();

        Rect displayRectangle = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
        myLayout.setMinimumWidth((int)(displayRectangle.width() * 0.9f));
       // myLayout.setMinimumHeight((int)(displayRectangle.height() * 0.7f));

        return myLayout;
    }

    @Override
    public void onSaveInstanceState(Bundle bundle){
        bundle.putLong(TIME, calendar.getTimeInMillis());
    }

    public void setCalendar(Calendar cal){
        calendar = cal;
    }

    public interface CalendarCloseListener {
        void onCalendarClose();
    }

    public void setListener(CalendarCloseListener listener){
        myListener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog){
        super.onDismiss(dialog);
        if(myListener != null){
            myListener.onCalendarClose();
        }
    }

    private void initWeekDays(){
        final Map<String, Integer> weekDays = calendar.getDisplayNames(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());

        ArrayList<String> weekDayList = new ArrayList<>(weekDays.keySet());

        Collections.sort(weekDayList, new Comparator<String>(){
            @Override
            public int compare(String a, String b){
                return weekDays.get(a) - weekDays.get(b);
            }
        });

        ((TextView) myLayout.findViewById(R.id.day1)).setText(weekDayList.get(0));
        ((TextView) myLayout.findViewById(R.id.day2)).setText(weekDayList.get(1));
        ((TextView) myLayout.findViewById(R.id.day3)).setText(weekDayList.get(2));
        ((TextView) myLayout.findViewById(R.id.day4)).setText(weekDayList.get(3));
        ((TextView) myLayout.findViewById(R.id.day5)).setText(weekDayList.get(4));
        ((TextView) myLayout.findViewById(R.id.day6)).setText(weekDayList.get(5));
        ((TextView) myLayout.findViewById(R.id.day7)).setText(weekDayList.get(6));
    }

    private void initDisplay(){
        myGrid = (GridView) myLayout.findViewById(R.id.calendar_grid);
        decMonth = (ImageButton) myLayout.findViewById(R.id.prev_month_button);
        incMonth = (ImageButton) myLayout.findViewById(R.id.next_month_button);
        myMonth = (TextView) myLayout.findViewById(R.id.current_month);
        decYear = (ImageButton) myLayout.findViewById(R.id.prev_year_button);
        incYear = (ImageButton) myLayout.findViewById(R.id.next_year_button);
        myYear = (TextView) myLayout.findViewById(R.id.current_year);
    }

    private void initOnClick(){

        decMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.add(Calendar.MONTH, -1);
                refresh();
            }
        });

        incMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.add(Calendar.MONTH, 1);
                refresh();
            }
        });

        decYear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.add(Calendar.YEAR, -1);
                refresh();
            }
        });

        incYear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.add(Calendar.YEAR, 1);
                refresh();
            }
        });

        myGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Date date = (Date) parent.getItemAtPosition(position);
                if(calendar.get(Calendar.MONTH) != date.getMonth()){
                    refresh();
                }
                calendar.setTime(date);
            }
        });

        ImageButton closer = (ImageButton) myLayout.findViewById(R.id.close_calendar);
        closer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    //Redraws the calendar display
    public void refresh(){
        ArrayList<Date> dateList = new ArrayList<>();
        Calendar populator = (Calendar) calendar.clone();

        int totalDays = getNumDaysInMonth(populator);
        populateListOfDates(dateList, populator, totalDays);

        myYear.setText(String.valueOf(calendar.get(Calendar.YEAR)));
        myMonth.setText(calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()));
        myGrid.setAdapter(new CalendarAdapter(getContext(), dateList));
    }

    //Populates an arraylist with dates
    private void populateListOfDates(ArrayList<Date> dateList, Calendar populator, int totalDays) {
        while(dateList.size() < totalDays){
            dateList.add(populator.getTime());
            populator.add(Calendar.DAY_OF_MONTH, 1);
        }

        int remainingDays = ((DAYSINWEEK + 1)-(populator.get(Calendar.DAY_OF_WEEK)))%DAYSINWEEK;

        for(int i = 0; i < remainingDays; i ++){
            dateList.add(populator.getTime());
            populator.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    //Calculates the total number of days from most recent Sunday to the first Saturday following the end of the current month to fill the calendar view
    private int getNumDaysInMonth(Calendar cal){
        int totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        //Day of week for the first of the month
        int monthStartWeekDay = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysSincePreviousSunday = -monthStartWeekDay;

        totalDays += monthStartWeekDay;

        //Backtracking calendar to the most recent Sunday
        cal.add(Calendar.DAY_OF_MONTH, daysSincePreviousSunday);

        return totalDays;
    }

    private class CalendarAdapter extends ArrayAdapter<Date>{

        private LayoutInflater mInflater;

        public CalendarAdapter(Context context, ArrayList<Date> dates){
            super(context, R.layout.calendar_date, dates);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent){
            if(view == null){
                view = mInflater.inflate(R.layout.calendar_date, null);
            }

            TextView text = (TextView) view;

            Date date = getItem(position);
            text.setText(String.valueOf(date.getDate()));

            Date current = calendar.getTime();

            if(date.equals(current)){
                text.setTextColor(getResources().getColor(R.color.white));
                text.setBackgroundColor(getResources().getColor(R.color.cc_attention_positive_color));
            }
            else if(date.getMonth() != current.getMonth()){
                text.setTextColor(getResources().getColor(R.color.grey_dark));
                text.setBackgroundColor(getResources().getColor(R.color.grey_lighter));
            }
            else{
                text.setTextColor(getResources().getColor(R.color.black));
                text.setBackgroundColor(getResources().getColor(R.color.transparent));
            }
            return text;
        }
    }

    public DateData getValue() {
        return new DateData(calendar.getTime());
    }

    public void clear() {
        calendar = Calendar.getInstance();
        refresh();
    }

    public void setDate(DateData newDate){
        Date nextDate = (Date) newDate.getValue();
        calendar.setTimeInMillis(nextDate.getTime());
        refresh();
    }
}

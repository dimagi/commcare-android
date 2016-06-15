package org.commcare.views.widgets;

import android.content.DialogInterface;
import android.content.Context;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Saumya on 5/29/2016.
 * DialogFragment for a popup calendar icon
 * Uses support library for compatibility with pre-honeycomb devices
 */
public class CalendarFragment extends android.support.v4.app.DialogFragment {

    private GridView myGrid;
    private ImageButton decMonth;
    private ImageButton incMonth;
    private ImageButton decYear;
    private ImageButton incYear;
    private TextView myMonth;
    private TextView myYear;
    private Calendar myCal;
    private LinearLayout myLayout;
    private DismissListener myListener;

    private String[] monthNames;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        myLayout = (LinearLayout) inflater.inflate(R.layout.calendar_widget, container);

        if(myCal == null){
            myCal = Calendar.getInstance();
        }

        initDisplay();
        initMonths();
        initWeekDays();
        initOnClick();
        refresh();

        Rect displayRectangle = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
        myLayout.setMinimumWidth((int)(displayRectangle.width() * 0.9f));

        return myLayout;

    }

    public void setArguments(Calendar cal){
        myCal = cal;
    }
    public interface DismissListener{
        void onDismiss();
    }

    public void setListener(DismissListener listener){
        myListener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog){
        super.onDismiss(dialog);
        if(myListener != null){
            myListener.onDismiss();
        }
    }

    private void initWeekDays(){
        final Map<String, Integer> weekDays = myCal.getDisplayNames(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());

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

    private void initMonths(){
        monthNames = new String[12];

        final Map<String, Integer> monthMap = myCal.getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        List<String> monthList = new ArrayList<>(monthMap.keySet());

        Collections.sort(monthList, new Comparator<String>(){
            @Override
            public int compare(String a, String b){
                return monthMap.get(a) - monthMap.get(b);
            }
        });

        monthNames = monthList.toArray(monthNames);
    }

    private void initDisplay(){
        myGrid = (GridView) myLayout.findViewById(R.id.calendar_grid);

        decMonth = (ImageButton) myLayout.findViewById(R.id.prevmonthbutton);
        incMonth = (ImageButton) myLayout.findViewById(R.id.nextmonthbutton);
        myMonth = (TextView) myLayout.findViewById(R.id.currentmonth);

        decYear = (ImageButton) myLayout.findViewById(R.id.prevyearbutton);
        incYear = (ImageButton) myLayout.findViewById(R.id.nextyearbutton);

        myYear = (TextView) myLayout.findViewById(R.id.currentyear);

    }

    private void initOnClick(){

        decMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.MONTH, -1);
                refresh();
            }
        });

        incMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.MONTH, 1);
                refresh();
            }
        });

        decYear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.YEAR, -1);
                refresh();
            }
        });

        incYear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myCal.add(Calendar.YEAR, 1);
                refresh();
            }
        });

        myGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Date date = (Date) parent.getItemAtPosition(position);
                myCal.setTime(date);
                refresh();
            }
        });

        ImageButton closer = (ImageButton) myLayout.findViewById(R.id.closecalendar);
        closer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    public void refresh(){

        ArrayList<Date> dateList = new ArrayList<>();
        Calendar populator = (Calendar) myCal.clone();

        int totalDays = populator.getActualMaximum(Calendar.DAY_OF_MONTH);

        populator.set(Calendar.DAY_OF_MONTH, 1);

        //Day of week for the first of the month
        int monthStartWeekDay = populator.get(Calendar.DAY_OF_WEEK) - 1;

        totalDays += monthStartWeekDay;

        //Backtracking calendar to the most recent Sunday
        populator.add(Calendar.DAY_OF_MONTH, -monthStartWeekDay);

        while(dateList.size() < totalDays){
            dateList.add(populator.getTime());
            populator.add(Calendar.DAY_OF_MONTH, 1);
        }

        int remainingDays = (8-populator.get(Calendar.DAY_OF_WEEK))%7;

        for(int i = 0; i < remainingDays; i ++){
            dateList.add(populator.getTime());
            populator.add(Calendar.DAY_OF_MONTH, 1);
        }

        myYear.setText(String.valueOf(myCal.get(Calendar.YEAR)));
        myMonth.setText(myCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()));
        myGrid.setAdapter(new CalendarAdapter(getContext(), dateList));
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

            Date current = myCal.getTime();

            if(date.equals(current)){
                text.setTextColor(getResources().getColor(R.color.white));
                text.setBackgroundColor(getResources().getColor(R.color.cc_attention_positive_color));
            }

            if(date.getMonth() != current.getMonth()){
                text.setTextColor(getResources().getColor(R.color.grey_dark));
                text.setBackgroundColor(getResources().getColor(R.color.grey_lighter));
            }

            text.setHeight(105);
            return text;
        }
    }

    public DateData getValue() {
        return new DateData(myCal.getTime());
    }

    public void clear() {
        myCal = Calendar.getInstance();
        refresh();
    }

    public void setDate(DateData newDate){
        Date nextDate = (Date) newDate.getValue();
        myCal.setTimeInMillis(nextDate.getTime());
        refresh();
    }
}

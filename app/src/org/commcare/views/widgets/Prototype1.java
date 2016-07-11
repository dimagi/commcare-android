package org.commcare.views.widgets;

import android.content.Context;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.Serializable;
import java.util.Calendar;


/**
 * Created by Saumya on 6/1/2016.
 * Prototype 1 of 3
 */
public class Prototype1 extends GregorianDateWidget implements CalendarFragment.CalendarCloseListener{

    private CalendarFragment myCalendarFragment;
    private ImageButton openCalButton;
    private FragmentManager fm;

    public Prototype1(Context con, FormEntryPrompt prompt){
        super(con, prompt);
        fm = ((FragmentActivity) getContext()).getSupportFragmentManager();
        myCalendarFragment = new CalendarFragment();
        myCalendarFragment.setCalendar(calendar);

        openCalButton = (ImageButton) findViewById(R.id.open_calendar_bottom);
        openCalButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });

        myCalendarFragment.setListener(this);
    }

    protected void openCalendar() {
        setFocus(getContext());
        myCalendarFragment.show(fm, "Calendar Popup");
    }

    @Override
    public void onCalendarClose() {
        Log.d("OTHER TIME", String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
        refreshDisplay();
        setFocus(getContext());
    }
}

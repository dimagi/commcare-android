package org.commcare.views.widgets;

import android.content.Context;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.commcare.dalvik.R;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;


/**
 * Created by Saumya on 6/1/2016.
 */
public class Prototype1 extends GregorianDateWidget implements CalendarFragment.DismissListener {

    private CalendarFragment myCalendar;
    private View calendarView;
    private ImageButton openCalButton;
    private FragmentManager fm;

    public Prototype1(Context con, FormEntryPrompt prompt){
        super(con, prompt);
        fm = ((FragmentActivity) getContext()).getSupportFragmentManager();
        myCalendar = new CalendarFragment();
        myCalendar.setArguments(getMyCalendar());
        calendarView = myCalendar.getView();

        openCalButton = (ImageButton) findViewById(R.id.opencalendar);
        openCalButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });

        myCalendar.setListener(this);
    }

    protected void openCalendar() {
        setFocus(getContext());
        myCalendar.show(fm, "Calendar Popup");
    }

    @Override
    public void onDismiss() {
        super.refresh();
    }
}

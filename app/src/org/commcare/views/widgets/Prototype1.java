package org.commcare.views.widgets;

import android.content.Context;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.commcare.dalvik.R;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;


/**
 * Created by Saumya on 6/1/2016.
 */
public class Prototype1 extends GregorianDateWidget {

    private CalendarWidget myCal;
    private ImageButton openCalButton;

    public Prototype1(Context con, FormEntryPrompt prompt){
        super(con, prompt);

        myCal = new CalendarWidget(con, prompt, getMyCal());
        myCal.setVisibility(GONE);
        myCal.removeQuestionText();
        addView(myCal);

        initButtons();
    }

    private void initButtons() {

        openCalButton = (ImageButton) findViewById(R.id.opencalendar);
        openCalButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });

        ImageButton calendarCloser = new ImageButton(getContext());
        calendarCloser.setMaxWidth(75);
        calendarCloser.setMaxHeight(75);

        calendarCloser.setBackgroundResource(R.drawable.green_check_mark);

        LinearLayout calendarinfo = (LinearLayout) findViewById(R.id.calendarinfo);
        calendarinfo.addView(calendarCloser);
        LinearLayout.LayoutParams calendarParams = (LinearLayout.LayoutParams) calendarCloser.getLayoutParams();
        calendarParams.width = 50;
        calendarParams.height = 50;

        calendarCloser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCalendar();
            }
        });
    }

    protected void openCalendar() {
        myCal.refresh();
        setFocus(getContext());
        myView.setVisibility(GONE);
        myCal.setVisibility(VISIBLE);
    }

    protected void closeCalendar(){
        refresh();
        myCal.setVisibility(GONE);
        myView.setVisibility(VISIBLE);
    }

    @Override
    public IAnswerData getAnswer() {
        if(myCal.getVisibility() != GONE){
            closeCalendar();
        }
        return super.getAnswer();
    }
}

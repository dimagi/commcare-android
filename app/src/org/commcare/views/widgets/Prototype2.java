package org.commcare.views.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.utils.UniversalDate;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
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

import static org.commcare.utils.UniversalDate.MILLIS_IN_DAY;

/**
 * Created by Saumya on 6/2/2016.
 */
public class Prototype2 extends QuestionWidget {

    private Prototype1 myPro1;

    public Prototype2(Context context, FormEntryPrompt prompt){
        super(context, prompt);
        myPro1 = new Prototype1(context, prompt);
        initView();
        addView(myPro1);
    }

    private void initView(){
        (myPro1.findViewById(R.id.dayupbtn)).setVisibility(GONE);
        (myPro1.findViewById(R.id.daydownbtn)).setVisibility(GONE);
        (myPro1.findViewById(R.id.monthupbtn)).setVisibility(GONE);
        (myPro1.findViewById(R.id.monthdownbtn)).setVisibility(GONE);
        (myPro1.findViewById(R.id.yearupbtn)).setVisibility(GONE);
        (myPro1.findViewById(R.id.yeardownbtn)).setVisibility(GONE);
        (myPro1.findViewById(R.id.clearall)).setVisibility(GONE);
        myPro1.findViewById(R.id.widgetinfo).setVisibility(GONE);
        myPro1.removeQuestionText();

        ImageButton openCalButton = new ImageButton(getContext());
        openCalButton.setBackgroundResource(R.drawable.avatar_vellum_date);
        myPro1.getMyGreg().addView(openCalButton);
        myPro1.addCalendarButton(openCalButton);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) openCalButton.getLayoutParams();
        params.leftMargin = 700;
    }

    @Override
    public IAnswerData getAnswer() {
        return myPro1.getAnswer();
    }

    @Override
    public void clearAnswer() {
        myPro1.clearAnswer();
    }

    @Override
    public void setFocus(Context context) {

    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {

    }

    public GregorianDateWidget getMyGreg(){
        return myPro1.getMyGreg();
    }

    public void removeQuestionText(){
        mQuestionText.setVisibility(GONE);
    }

}

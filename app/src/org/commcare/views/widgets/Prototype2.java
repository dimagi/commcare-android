package org.commcare.views.widgets;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
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
public class Prototype2 extends Prototype1 {

    public Prototype2(Context context, FormEntryPrompt prompt){
        super(context, prompt);
        initView();
    }

    private void initView(){
        findViewById(R.id.dayupbtn).setVisibility(GONE);
        findViewById(R.id.daydownbtn).setVisibility(GONE);
        findViewById(R.id.monthupbtn).setVisibility(GONE);
        findViewById(R.id.monthdownbtn).setVisibility(GONE);
        findViewById(R.id.yearupbtn).setVisibility(GONE);
        findViewById(R.id.yeardownbtn).setVisibility(GONE);
        findViewById(R.id.widgetinfo).setVisibility(GONE);

        ImageButton openCalendar = (ImageButton) findViewById(R.id.opencalendarbottom);
        openCalendar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });
        openCalendar.setVisibility(VISIBLE);

    }
}

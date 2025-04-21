package org.commcare.views;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;


import org.commcare.dalvik.R;
import org.commcare.utils.DotsData;
import org.commcare.utils.DotsData.DotsDay;
import org.commcare.utils.DotsData.MedStatus;
import org.commcare.utils.DotsEditListener;

import java.util.Calendar;

/**
 * @author ctsims
 */
public class DotsHomeView extends RelativeLayout {

    private final DotsData data;
    private final DotsEditListener listener;
    private static final String[] dayArray = new String[]{"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};

    private static final int TABLE_LENGTH = 7;

    public DotsHomeView(Context context, DotsData data, DotsEditListener listener) {
        super(context);
        this.data = data;
        this.listener = listener;
        refresh();
    }

    private void refresh() {
        this.removeAllViews();

        TableLayout table = new TableLayout(this.getContext());

        int days = data.days().length;
        int rows = (int)Math.ceil(days / TABLE_LENGTH);

        View[] dayViews = new View[days];

        table.setShrinkAllColumns(true);
        table.setStretchAllColumns(true);

        TableRow[] tRows = new TableRow[rows];

        for (int i = 0; i < tRows.length; ++i) {
            tRows[i] = new TableRow(this.getContext());
        }

        Calendar c = Calendar.getInstance();
        c.setTime(data.anchor());
        c.add(Calendar.DATE, -(data.days().length - 1));

        int currentRow = 0;

        for (int i = 0; i < days; ++i) {
            DotsDay day = data.days()[i];

            TableRow.LayoutParams dayParams = new TableRow.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            dayParams.setMargins(1, 1, 1, 1);


            View dayView = getDayView(c, day, i);

            tRows[currentRow].addView(dayView);
            dayViews[i] = dayView;
            if (i % TABLE_LENGTH == TABLE_LENGTH - 1) {
                currentRow = currentRow + 1;
            }

            c.add(Calendar.DATE, 1);
        }

        for (TableRow row : tRows) {
            table.addView(row);
        }

        Button done = new MaterialButton(this.getContext());
        done.setId(666);
        done.setText("Finished");
        done.setOnClickListener(v -> listener.doneWithDOTS());


        RelativeLayout topPane = new RelativeLayout(this.getContext());
        LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ABOVE, done.getId());
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        this.addView(topPane, params);

        table.setGravity(Gravity.CENTER_VERTICAL);
        params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
        topPane.addView(table, params);

        params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        this.addView(done, params);
    }


    private View getDayView(Calendar c, DotsDay d, final int dayIndex) {
        View dayView = View.inflate(this.getContext(), R.layout.dotsday, null);

        TextView date = dayView.findViewById(R.id.text_date);
        TextView dow = dayView.findViewById(R.id.text_dow);

        dow.setText(dayArray[c.get(Calendar.DAY_OF_WEEK) - 1]);
        date.setText((c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH));

        ImageView icon = dayView.findViewById(R.id.day_icon);
        MedStatus s = d.status();
        if (s == MedStatus.empty) {
            icon.setImageResource(R.drawable.checkmark);
        } else if (s == MedStatus.partial) {
            icon.setImageResource(R.drawable.blues);
        } else {
            icon.setVisibility(View.INVISIBLE);
        }

        dayView.setOnClickListener(v -> {
            Rect hitRect = new Rect();
            if (v.getParent() instanceof View) {
                v.getHitRect(hitRect);
                View parent = (View)v.getParent();
                DotsHomeView.this.offsetDescendantRectToMyCoords(parent, hitRect);
                listener.editDotsDay(dayIndex, hitRect);
            } else {
                hitRect = new Rect(0, 0, v.getWidth(), v.getHeight());
                DotsHomeView.this.offsetDescendantRectToMyCoords(v, hitRect);
                listener.editDotsDay(dayIndex, hitRect);
            }
        });

        return dayView;
    }
}

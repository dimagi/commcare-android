package org.commcare.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.suite.model.Text;
import org.commcare.utils.MarkupUtil;

import java.text.DateFormat;
import java.util.Date;
import java.util.Hashtable;

/**
 * Individual form listing view for the incomplete & saved forms lists
 *
 * @author ctsims
 */
public class IncompleteFormRecordView extends LinearLayout {

    public final TextView mPrimaryTextView;
    private final TextView mLowerTextView;
    public final TextView mRightTextView;
    private final TextView mUpperRight;

    private final Hashtable<String, Text> names;
    private final Date start;

    private final Drawable rightHandSync;

    public IncompleteFormRecordView(Context context, Hashtable<String, Text> names) {
        super(context);

        ViewGroup vg = (ViewGroup)View.inflate(context, R.layout.formrecordview, null);
        this.names = names;

        mPrimaryTextView = (TextView)vg.findViewById(R.id.formrecord_txt_main);
        mLowerTextView = (TextView)vg.findViewById(R.id.formrecord_txt_btm);
        mRightTextView = (TextView)vg.findViewById(R.id.formrecord_txt_right);
        mUpperRight = (TextView)vg.findViewById(R.id.formrecord_txt_upp_right);

        mPrimaryTextView.setTextAppearance(context, android.R.style.TextAppearance_Large);
        mUpperRight.setTextAppearance(context, android.R.style.TextAppearance_Large);

        LayoutParams l = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        addView(vg, l);

        start = new Date();

        rightHandSync = context.getResources().getDrawable(android.R.drawable.stat_notify_sync_noanim);
    }

    public void setParams(FormRecord record, String dataTitle, Long timestamp) {
        if (names.containsKey(record.getFormNamespace())) {
            Text name = names.get(record.getFormNamespace());
            mPrimaryTextView.setText(MarkupUtil.styleSpannable(getContext(), name.evaluate()));
        } else {
            mPrimaryTextView.setText(MarkupUtil.localizeStyleSpannable(getContext(), "form.record.gone"));
        }

        if (dataTitle != null) {
            mLowerTextView.setText(dataTitle);
        }

        //be careful here...
        if (timestamp != 0) {
            mRightTextView.setText(DateUtils.formatSameDayTime(timestamp, start.getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT));
        } else {
            mRightTextView.setText("Never");
        }
        if (FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
            mUpperRight.setText(MarkupUtil.localizeStyleSpannable(getContext(), "form.record.unsent"));
            mUpperRight.setTextAppearance(getContext(), R.style.WarningTextStyle);
            mUpperRight.setCompoundDrawablesWithIntrinsicBounds(null, null, rightHandSync, null);
        } else {
            mUpperRight.setText("");
            mUpperRight.setCompoundDrawables(null, null, null, null);
        }
    }
}

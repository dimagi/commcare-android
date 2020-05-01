package org.commcare.views;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.activities.FormRecordListActivity;
import org.commcare.dalvik.R;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.suite.model.Text;
import org.commcare.utils.MarkupUtil;
import org.commcare.utils.QuarantineUtil;
import org.javarosa.core.services.locale.Localization;

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
    private final ImageView syncIcon;
    private final TextView reasonForQuarantineView;

    private final Date start;

    public IncompleteFormRecordView(Context context) {
        super(context);

        ViewGroup vg = (ViewGroup)View.inflate(context, R.layout.formrecordview, null);
        mPrimaryTextView = vg.findViewById(R.id.formrecord_txt_main);
        mLowerTextView = vg.findViewById(R.id.formrecord_txt_btm);
        mRightTextView = vg.findViewById(R.id.formrecord_txt_right);
        mUpperRight = vg.findViewById(R.id.formrecord_txt_upp_right);
        syncIcon = vg.findViewById(R.id.formrecord_sync_icon);
        reasonForQuarantineView = vg.findViewById(R.id.reason_for_quarantine_display);

        mPrimaryTextView.setTextAppearance(context, android.R.style.TextAppearance_Large);
        mUpperRight.setTextAppearance(context, android.R.style.TextAppearance_Large);

        LayoutParams l = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(vg, l);

        start = new Date();
    }

    public void setParams(FormRecord record, String dataTitle,
                          Long timestamp, Hashtable<String, Text> names) {
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
        if (FormRecordListActivity.FormRecordFilter.Pending.containsStatus(record.getStatus())) {
            mUpperRight.setText(MarkupUtil.localizeStyleSpannable(getContext(), "form.record.unsent"));
            mUpperRight.setTextAppearance(getContext(), R.style.WarningTextStyle);

            syncIcon.setVisibility(View.VISIBLE);
        } else {
            mUpperRight.setText("");
            syncIcon.setVisibility(View.GONE);
        }

        if (FormRecord.STATUS_QUARANTINED.equals(record.getStatus())) {
            reasonForQuarantineView.setVisibility(View.VISIBLE);
            reasonForQuarantineView.setText(
                    Localization.get("reason.for.quarantine.prefix") +
                    QuarantineUtil.getQuarantineReasonDisplayString(record, false));
        } else {
            reasonForQuarantineView.setVisibility(View.GONE);
        }
    }
}

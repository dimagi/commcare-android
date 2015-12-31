/**
 *
 */
package org.commcare.android.adapters;

import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.provider.CallLog.Calls;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author ctsims
 */
public class CallRecordAdapter implements ListAdapter {
    Context context;
    Cursor cursor;
    List<Integer> enabled = new ArrayList<>();

    public CallRecordAdapter(Context context, Cursor c) {
        this.context = context;
        this.cursor = c;
        if (c.moveToFirst()) {
            while (!c.isAfterLast()) {
                int index = cursor.getColumnIndex(Calls.NUMBER);
                if (index != -1) {
                    String num = cursor.getString(index);
                    if (num != null) {
                        String name = CommCareApplication._().getCallListener().getCaller(num);
                        if (name != null) {
                            enabled.add(c.getPosition());
                        }
                    }
                }

                c.moveToNext();
            }
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public int getCount() {
        return enabled.size();
    }

    @Override
    public Object getItem(int position) {
        cursor.moveToPosition(enabled.get(position));
        return cursor.getString(cursor.getColumnIndex(Calls.NUMBER));
    }

    @Override
    public long getItemId(int position) {
        return enabled.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        cursor.moveToPosition(enabled.get(position));

        View cre = convertView;
        if (cre == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            cre = inflater.inflate(R.layout.call_record_entry, null);
        }

        TextView name = (TextView) cre.findViewById(R.id.call_log_name);
        TextView number = (TextView) cre.findViewById(R.id.call_log_number);
        TextView when = (TextView) cre.findViewById(R.id.call_log_when);

        ImageView icon = (ImageView) cre.findViewById(R.id.call_status_icon);


        String phoneNumber = cursor.getString(cursor.getColumnIndex(Calls.NUMBER));
        String callerName = CommCareApplication._().getCallListener().getCaller(phoneNumber);

        name.setText(callerName);
        number.setText(phoneNumber);

        when.setText(DateUtils.formatSameDayTime(new Date(cursor.getLong(cursor.getColumnIndex(Calls.DATE))).getTime(), new Date().getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT));

        int iconResource = android.R.drawable.stat_sys_phone_call;

        switch (Integer.parseInt(cursor.getString(
                cursor.getColumnIndex(Calls.TYPE)))) {
            case 1:
                iconResource = android.R.drawable.sym_call_incoming;
                break;
            case 2:
                iconResource = android.R.drawable.sym_call_outgoing;
                break;
            case 3:
                iconResource = android.R.drawable.sym_call_missed;
                break;
        }


        icon.setImageResource(iconResource);

        return cre;

    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return this.getCount() > 0;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
    }
}

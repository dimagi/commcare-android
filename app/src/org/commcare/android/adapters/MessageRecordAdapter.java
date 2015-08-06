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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ctsims
 *
 */
public class MessageRecordAdapter implements ListAdapter {
    Context context;
    Cursor cursor;
    List<Integer> enabled = new ArrayList<Integer>();
    Set<Long> seen = new HashSet<Long>();
    
    public static final int MAX = 100;
    
    public MessageRecordAdapter(Context context, Cursor c) {
        this.context = context;
        this.cursor = c;
        int counted = 0;
        if(c.moveToFirst()) {
            while(!c.isAfterLast() && counted < MAX) {
                int index = cursor.getColumnIndex("address");
                if(index != -1) {
                    long threadid = cursor.getLong(cursor.getColumnIndex("thread_id"));
                    if(!seen.contains(threadid)) {
                        String num = cursor.getString(index);
                        if(num != null) {
                            String name = CommCareApplication._().getCallListener().getCaller(num);
                            if(name != null) {
                                enabled.add(c.getPosition());
                                counted++;
                            }
                            seen.add(threadid);
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
        return cursor.getString(cursor.getColumnIndex("address"));
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
        
        TextView name = (TextView)cre.findViewById(R.id.call_log_name);
        TextView number = (TextView)cre.findViewById(R.id.call_log_number);
        TextView when = (TextView)cre.findViewById(R.id.call_log_when);
        
        ImageView icon = (ImageView)cre.findViewById(R.id.call_status_icon);
        

        String phoneNumber = cursor.getString(cursor.getColumnIndex("address"));
        String callerName = CommCareApplication._().getCallListener().getCaller(phoneNumber);
        
        name.setText(callerName);
        number.setText(phoneNumber);
        
        when.setText(DateUtils.formatSameDayTime(new Date(cursor.getLong(cursor.getColumnIndex(Calls.DATE))).getTime(), new Date().getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT));
        
        int iconResource = android.R.drawable.stat_sys_phone_call;
        
        switch (cursor.getInt(cursor.getColumnIndex("read")))
                {
                      case 0: iconResource = R.drawable.ic_unread_message;
                         break;
                      case 1: iconResource = R.drawable.ic_read_message;
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

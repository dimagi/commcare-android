/**
 * 
 */
package org.commcare.dalvik.activities;

import java.text.DateFormat;
import java.util.ArrayList;

import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

import android.app.ListActivity;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * An activity to display messages for the user about something that
 * happened which might not be easy to explain.
 * 
 * @author ctsims
 *
 */
public class MessageActivity extends ListActivity {
    ArrayList<NotificationMessage> messages;
    
    private static final String KEY_MESSAGES = "ma_key_messages";

    /* (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null && savedInstanceState.containsKey(KEY_MESSAGES)) {
            messages = savedInstanceState.getParcelableArrayList(KEY_MESSAGES);
        } else {
            messages = CommCareApplication._().purgeNotifications();
        }
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(KEY_MESSAGES, messages);
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        this.setContentView(R.layout.screen_messages);
        this.setListAdapter(new ArrayAdapter<NotificationMessage>(this, R.layout.layout_note_msg, messages) {

            /* (non-Javadoc)
             * @see android.widget.ArrayAdapter#getView(int, android.view.View, android.view.ViewGroup)
             */
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View messageView = convertView;
                if(convertView == null) {
                    messageView = LayoutInflater.from(MessageActivity.this).inflate(R.layout.layout_note_msg, parent, false);
                }
                NotificationMessage msg = this.getItem(position);
                TextView title = (TextView)messageView.findViewById(R.id.layout_note_msg_title);
                TextView body = (TextView)messageView.findViewById(R.id.layout_note_msg_body);
                TextView date = (TextView)messageView.findViewById(R.id.layout_note_msg_date);
                TextView action = (TextView)messageView.findViewById(R.id.layout_note_msg_action);
                title.setText(msg.getTitle());
                body.setText(msg.getDetails());
                date.setText(DateUtils.formatSameDayTime(msg.getDate().getTime(), System.currentTimeMillis(), DateFormat.DEFAULT, DateFormat.DEFAULT));
                
                String actionText = msg.getAction();
                if(actionText == null) { action.setVisibility(View.GONE); }
                else { action.setText(actionText); }
                
                return messageView;
            }

            /* (non-Javadoc)
             * @see android.widget.BaseAdapter#isEnabled(int)
             */
            @Override
            public boolean isEnabled(int position) {
                return false;
            }
            
        });
    }
    
    
}

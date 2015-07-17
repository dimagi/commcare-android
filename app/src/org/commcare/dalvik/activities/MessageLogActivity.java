package org.commcare.dalvik.activities;

import org.commcare.android.adapters.MessageRecordAdapter;
import org.commcare.dalvik.R;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * @author ctsims
 */
public class MessageLogActivity extends ListActivity {

    LinearLayout header;
    MessageRecordAdapter messages;
    
    boolean isMessages = false;
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(getString(R.string.application_name) + " > " + "Message Logs");
        
        refreshView();
    }

    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
        messages = new MessageRecordAdapter(this, this.getContentResolver().query(Uri.parse("content://sms"),new String[] {"_id","address","date","type","read","thread_id"}, "type=?", new String[] {"1"}, "date" + " DESC"));
        this.setListAdapter(messages);
    }

    /*
     * (non-Javadoc)
     * @see android.app.ListActivity#onListItemClick(android.widget.ListView, android.view.View, int, long)
     * 
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        String number = (String)messages.getItem(position);
        Intent i = new Intent(this, CallOutActivity.class);
        i.putExtra(CallOutActivity.PHONE_NUMBER, number);
        i.putExtra(CallOutActivity.INCOMING_ACTION, Intent.ACTION_SENDTO);
        startActivity(i);
        return;
    }
}

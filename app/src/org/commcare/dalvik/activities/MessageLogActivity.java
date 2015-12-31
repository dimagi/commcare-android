package org.commcare.dalvik.activities;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

import org.commcare.android.adapters.MessageRecordAdapter;
import org.commcare.dalvik.R;

/**
 * @author ctsims
 */
public class MessageLogActivity extends ListActivity {
    MessageRecordAdapter messages;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

    /**
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

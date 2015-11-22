package org.commcare.dalvik.activities;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.commcare.android.adapters.CallRecordAdapter;
import org.commcare.android.adapters.MessageRecordAdapter;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.storage.Persistable;

/**
 * @author ctsims
 */
public class CallLogActivity<T extends Persistable> extends ListActivity {
    CallRecordAdapter calls;
    MessageRecordAdapter messages;
    
    private static final String EXTRA_MESSAGES = "cla_messages";
    
    boolean isMessages = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(getString(R.string.application_name) + " > " + "Phone Logs");
        
        isMessages = getIntent().getBooleanExtra(EXTRA_MESSAGES, false);
        
        refreshView();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_MESSAGES,isMessages);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        inState.getBoolean(EXTRA_MESSAGES, false);
    }

    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
        ListAdapter adapter;
        if(isMessages) {
            if(messages == null) {
                messages = new MessageRecordAdapter(this, this.getContentResolver().query(Uri.parse("content://sms"),new String[] {"_id","address","date","type","read","thread_id"}, "type=?", new String[] {"1"}, "date" + " DESC"));
            }
            adapter = messages;
        } else {
            if (calls == null) {
                Cursor callCursor = null;
                try {
                    callCursor = getContentResolver().query(android.provider.CallLog.Calls.CONTENT_URI, null, null, null, Calls.DATE + " DESC");
                    calls = new CallRecordAdapter(this, callCursor);
                } finally {
                    if (callCursor != null && !callCursor.isClosed()) {
                        callCursor.close();
                    }
                }
            }
            adapter = calls;
        }

        this.setListAdapter(adapter);
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        if(isMessages) {
            String number = (String)messages.getItem(position);
            Intent i = new Intent(this, CallOutActivity.class);
            i.putExtra(CallOutActivity.PHONE_NUMBER, number);
            i.putExtra(CallOutActivity.INCOMING_ACTION, Intent.ACTION_SENDTO);
            startActivity(i);
        } else {
            String number = (String)calls.getItem(position);
            Intent detail = CommCareApplication._().getCallListener().getDetailIntent(this,number);
            if(detail == null) {
                //Start normal callout activity
                Intent i = new Intent(this, CallOutActivity.class);
                i.putExtra(CallOutActivity.PHONE_NUMBER, number);
                i.putExtra(CallOutActivity.INCOMING_ACTION, Intent.ACTION_SENDTO);
                startActivity(i);
            } else {
                startActivity(detail);
            }
        }
    }
}

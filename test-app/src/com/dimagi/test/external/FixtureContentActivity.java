package com.dimagi.test.external;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class FixtureContentActivity extends Activity {
    
    public static final int KEY_REQUEST_CODE = 1;

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_page);
        showFixtureData(null, null);
    }
    
    
    
    protected void showFixtureData(String selection, String[] selectionArgs) {
        ListView la = (ListView)this.findViewById(R.id.list_view);
        Cursor c = this.managedQuery(Uri.parse("content://org.commcare.dalvik.fixture/"), null, selection, selectionArgs, null);
        
        final SimpleCursorAdapter sca = new SimpleCursorAdapter(this, android.R.layout.test_list_item, c, new String[] {"fixture_id"}, new int[] { android.R.id.text1});
        
        la.setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                Cursor cursor =  sca.getCursor();
                cursor.moveToPosition(position);
                   
                String fixtureId = cursor.getString(cursor.getColumnIndex("fixture_id"));
                
                FixtureContentActivity.this.showFixtureData("fixture_id = ?", new String[] {fixtureId});
            }
        });

        la.setAdapter(sca);
    }
    
    protected void moveToDataAtapter(String fixtureId) {
        Cursor c = this.managedQuery(Uri.parse("content://org.commcare.dalvik.fixture/" + fixtureId), null, null, null, null);
        
        SimpleCursorAdapter sca = new SimpleCursorAdapter(this, android.R.layout.test_list_item, c, new String[] {"value"}, new int[] { android.R.id.text1});
        ListView la = (ListView)this.findViewById(R.id.list_view);

        la.setAdapter(sca);
        la.setOnItemClickListener(null);

    }
    /* (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}

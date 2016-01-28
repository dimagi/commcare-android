package com.dimagi.test.external;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

/**
 * Test class used for testing the multimedia content provider of CommCareODK
 */

public class CaseMediaActivity extends Activity {

    public String CASE_URI_ROOT = "content://org.commcare.dalvik.case/casedb/";

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_page);
        showCaseData(null, null);
    }


    /**
     * Queries CommCare ODK and displays a list of the currently loaded cases
     *
     * @param selection
     * @param selectionArgs
     */
    private void showCaseData(String selection, String[] selectionArgs) {
        ListView la = (ListView)this.findViewById(R.id.list_view);
        Cursor c = this.managedQuery(Uri.parse(CASE_URI_ROOT + "case"), null, selection, selectionArgs, null);

        final SimpleCursorAdapter sca = new SimpleCursorAdapter(this, android.R.layout.two_line_list_item, c, new String[]{"case_name", "case_id"}, new int[]{android.R.id.text1, android.R.id.text2});

        la.setOnItemLongClickListener(new OnItemLongClickListener() {

            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                Cursor cursor = sca.getCursor();
                cursor.moveToPosition(position);

                String caseType = cursor.getString(cursor.getColumnIndex("case_type"));
                CaseMediaActivity.this.showCaseData("case_type = ? AND\nstatus=?", new String[]{caseType, "open"});
                return true;
            }
        });

        la.setAdapter(sca);
        la.setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
                Cursor cursor = sca.getCursor();
                cursor.moveToPosition(position);

                String caseId = cursor.getString(cursor.getColumnIndex("case_id"));
                CaseMediaActivity.this.moveToMediaAdapter(caseId);
            }

        });
    }

    /**
     * Queries CommCareODK for the multimedia associate with this case and displays
     *
     * @param caseId
     */

    private void moveToMediaAdapter(String caseId) {
        Cursor cursor = this.managedQuery(Uri.parse(CASE_URI_ROOT + "attachment/" + caseId), null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {

            String filePath = cursor.getString(cursor.getColumnIndex("file-source"));

            if (!"invalid".equals(filePath)) {
                ImageView imageView = (ImageView)this.findViewById(R.id.image_view);

                Bitmap mBmp = BitmapFactory.decodeFile(filePath);
                if (mBmp != null) {
                    imageView.setImageBitmap(mBmp);
                }
            }

            cursor.moveToNext();
        }

    }
}

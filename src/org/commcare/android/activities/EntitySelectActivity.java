/**
 * 
 */
package org.commcare.android.activities;

import org.commcare.android.R;
import org.commcare.android.adapters.EntityListAdapter;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.Case;
import org.commcare.android.util.CommCarePlatformProvider;
import org.commcare.util.CommCarePlatform;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

/**
 * @author ctsims
 *
 */
public class EntitySelectActivity extends ListActivity {
	private CommCarePlatform platform;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entity_select_layout);
        
        platform = CommCarePlatformProvider.unpack(getIntent().getBundleExtra(GlobalConstants.COMMCARE_PLATFORM));
        
        setTitle(getString(R.string.app_name) + " > " + " Select");
        
        refreshView();
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	setListAdapter(new EntityListAdapter(this, platform, new SqlIndexedStorageUtility(Case.STORAGE_KEY, Case.class.getName(), this)));
    }


    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        // create intent for return and store path
        Intent i = new Intent(this.getIntent());
        i.putExtra(GlobalConstants.CASE_ID, id);
        setResult(RESULT_OK, i);

        finish();
    }


}

package org.commcare.activities;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.commcare.dalvik.R;

public class CommcareListActivity extends AppCompatActivity {

    protected ListAdapter mAdapter;
    protected ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResource());
        mListView = (ListView)findViewById(R.id.list_view);
        if (mListView == null) {
            throw new RuntimeException(
                    "Your content must have a ListView whose id attribute is " +
                            "'android.R.id.list'");
        }

        View emptyView = findViewById(R.id.empty);
        if (emptyView != null) {
            mListView.setEmptyView(emptyView);
        }
        mListView.setOnItemClickListener(mOnClickListener);
    }

    public int getLayoutResource() {
        return R.layout.activity_commcare_list;
    }

    public void setListAdapter(ListAdapter adapter) {
        synchronized (this) {
            mAdapter = adapter;
            mListView.setAdapter(adapter);
        }
    }

    public ListAdapter getListAdapter() {
        return mAdapter;
    }

    public ListView getListView() {
        return mListView;
    }

    private AdapterView.OnItemClickListener mOnClickListener = (parent, v, position, id) -> onListItemClick((ListView)parent, v, position, id);

    /**
     * This method will be called when an item in the list is selected.
     * Subclasses should override. Subclasses can call
     * getListView().getItemAtPosition(position) if they need to access the
     * data associated with the selected item.
     *
     * @param l The ListView where the click happened
     * @param v The view that was clicked within the ListView
     * @param position The position of the view in the list
     * @param id The row id of the item that was clicked
     */
    protected void onListItemClick(ListView l, View v, int position, long id) {
    }
}

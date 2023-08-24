package org.commcare.activities;

import android.annotation.SuppressLint;
import android.view.ViewTreeObserver;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import org.commcare.adapters.AppSelectAdapter;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;

/**
 * UI Controller, handles UI interaction with the owning Activity
 *
 * @author dviggiano
 */
@ManagedUi(R.layout.screen_app_select)
public class AppSelectActivityUiController implements CommCareActivityUIController {
    @UiElement(value = R.id.app_select_grid)
    private RecyclerView gridView;

    private AppSelectAdapter adapter;

    protected final AppSelectActivity activity;

    public AppSelectActivityUiController(AppSelectActivity activity) {
        this.activity = activity;
    }

    @Override
    public void setupUI() {
        adapter = new AppSelectAdapter(activity);
        setupGridView();
    }

    @Override
    public void refreshView() {
        if (adapter != null) {
            // adapter can be null if backstack was cleared for memory reasons
            adapter.notifyDataSetChanged();
        }
    }

    private void setupGridView() {
        gridView.setHasFixedSize(false);

        StaggeredGridLayoutManager gridViewManager =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        gridView.setLayoutManager(gridViewManager);
        gridView.setItemAnimator(null);
        gridView.setAdapter(adapter);

        gridView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressLint("NewApi")
            @Override
            public void onGlobalLayout() {
                gridView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                gridView.requestLayout();
                adapter.notifyDataSetChanged();
                activity.rebuildOptionsMenu();
            }
        });
    }
}

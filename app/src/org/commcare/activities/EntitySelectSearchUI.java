package org.commcare.activities;

import android.annotation.TargetApi;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import org.commcare.activities.components.EntitySelectCalloutSetup;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Callout;

import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;

/**
 * Manages case list activity's search state and UI
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class EntitySelectSearchUI implements TextWatcher {
    private SearchView searchView;
    private MenuItem searchMenuItem;
    private MenuItem barcodeMenuItem;
    private TextView searchResultStatus;
    private ImageButton clearSearchButton;
    private View searchBanner;

    private final EntitySelectActivity activity;

    private String filterString = "";

    EntitySelectSearchUI(EntitySelectActivity activity) {
        this.activity = activity;
        initUIComponents();
    }

    private void initUIComponents() {
        searchBanner = activity.findViewById(R.id.search_result_banner);
        searchResultStatus = activity.findViewById(R.id.search_results_status);
        clearSearchButton = activity.findViewById(R.id.clear_search_button);
        clearSearchButton.setOnClickListener(v -> {
            activity.getAdapter().clearCalloutResponseData();
            activity.refreshView();
        });
        clearSearchButton.setVisibility(View.GONE);
    }

    protected CommCareActivity.ActionBarInstantiator getActionBarInstantiator() {
        // again, this should be unnecessary...
        return (searchItem, view, barcodeItem) -> {
            searchMenuItem = searchItem;
            searchView = view;
            barcodeMenuItem = barcodeItem;
            restoreLastQuery();

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    activity.setLastQueryString(newText);
                    filterString = newText;
                    if (activity.getAdapter() != null) {
                        activity.getAdapter().filterByString(newText);
                    }
                    return false;
                }
            });
        };
    }

    public void setupActionImage(Callout callout) {
        if (callout != null && callout.getImage() != null) {
            // Replace the barcode scan callout with our custom callout
            EntitySelectCalloutSetup.setupImageLayout(activity, barcodeMenuItem, callout.getImage());
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void restoreLastQuery() {
        String lastQueryString = activity.getLastQueryString();
        if (lastQueryString != null && lastQueryString.length() > 0) {
            searchMenuItem.expandActionView();
            filterString = lastQueryString;
            searchView.setQuery(lastQueryString, false);
            if (activity.getAdapter() != null) {
                activity.getAdapter().filterByString(lastQueryString);
            }
        }
    }

    @SuppressWarnings("NewApi")
    protected CharSequence getSearchText() {
        if (searchView != null) {
            return searchView.getQuery();
        }
        return "";
    }

    @SuppressWarnings("NewApi")
    protected void setSearchText(CharSequence text) {
        if (searchView != null) {
            searchMenuItem.expandActionView();
            searchView.setQuery(text, false);
        } else {
            activity.setLastQueryString(text.toString());
        }
    }

    @Override
    public void afterTextChanged(Editable incomingEditable) {
        final String incomingString = incomingEditable.toString();
        final String currentSearchText = getSearchText().toString();
        if (incomingString.equals(currentSearchText)) {
            filterString = currentSearchText;
            if (activity.getAdapter() != null) {
                activity.getAdapter().filterByString(filterString);
            }
        }
        if (searchView == null) {
            activity.setLastQueryString(filterString);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
                                  int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    protected void restoreSearchString() {
        if (filterString != null && !"".equals(filterString)) {
            activity.getAdapter().filterByString(filterString);
        }
    }

    protected void setSearchBannerState() {
        if (!"".equals(activity.getAdapter().getSearchQuery())) {
            showSearchBanner();
            // Android's native SearchView has its own clear search button, so need to add our own
            clearSearchButton.setVisibility(View.GONE);
        } else if (activity.getAdapter().isFilteringByCalloutResult()) {
            showSearchBanner();
            clearSearchButton.setVisibility(View.VISIBLE);
        } else {
            searchBanner.setVisibility(View.GONE);
            clearSearchButton.setVisibility(View.GONE);
        }
    }

    private void showSearchBanner() {
        searchResultStatus.setText(activity.getAdapter().getSearchNotificationText());
        searchResultStatus.setVisibility(View.VISIBLE);
        searchBanner.setVisibility(View.VISIBLE);
    }

}

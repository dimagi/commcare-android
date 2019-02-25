package org.commcare.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SearchView;
import android.widget.TextView;

import org.commcare.activities.components.EntitySelectCalloutSetup;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Callout;
import org.javarosa.core.services.locale.Localization;

/**
 * Manages case list activity's search state and UI
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class EntitySelectSearchUI implements TextWatcher {
    private SearchView searchView;
    private MenuItem searchMenuItem;
    private MenuItem barcodeMenuItem;
    private EditText preHoneycombSearchBox;
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
        return (searchItem, searchView, barcodeItem) -> {
            EntitySelectSearchUI.this.searchMenuItem = searchItem;
            EntitySelectSearchUI.this.searchView = searchView;
            EntitySelectSearchUI.this.barcodeMenuItem = barcodeItem;

            restoreLastQuery();

            EntitySelectSearchUI.this.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                searchMenuItem.expandActionView();
            }
            filterString = lastQueryString;
            searchView.setQuery(lastQueryString, false);
            if (activity.getAdapter() != null) {
                activity.getAdapter().filterByString(lastQueryString);
            }
        }
    }

    @SuppressWarnings("NewApi")
    protected CharSequence getSearchText() {
        if (isUsingActionBar()) {
            return searchView.getQuery();
        } else {
            return preHoneycombSearchBox.getText();
        }
    }

    @SuppressWarnings("NewApi")
    protected void setSearchText(CharSequence text) {
        if (isUsingActionBar()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                searchMenuItem.expandActionView();
            }
            searchView.setQuery(text, false);
        } else {
            preHoneycombSearchBox.setText(text);
        }
    }

    protected void setupPreHoneycombFooter(View.OnClickListener barcodeScanOnClickListener, Callout callout) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            TextView preHoneycombSearchLabel =
                    activity.findViewById(R.id.screen_entity_select_search_label);
            //use the old method here because some Android versions don't like Spannables for titles
            preHoneycombSearchLabel.setText(Localization.get("select.search.label"));
            preHoneycombSearchLabel.setOnClickListener(v -> {
                // get the focus on the edittext by performing click
                preHoneycombSearchBox.performClick();
                // then force the keyboard up since performClick() apparently isn't enough on some devices
                InputMethodManager inputMethodManager =
                        (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                // only will trigger it if no physical keyboard is open
                inputMethodManager.showSoftInput(preHoneycombSearchBox, InputMethodManager.SHOW_IMPLICIT);
            });

            preHoneycombSearchBox = activity.findViewById(R.id.searchbox);
            preHoneycombSearchBox.setMaxLines(3);
            preHoneycombSearchBox.setHorizontallyScrolling(false);
            preHoneycombSearchBox.addTextChangedListener(this);
            preHoneycombSearchBox.requestFocus();
            preHoneycombSearchBox.setText(activity.getLastQueryString());

            ImageButton preHoneycombBarcodeButton = activity.findViewById(R.id.barcodeButton);
            preHoneycombBarcodeButton.setOnClickListener(barcodeScanOnClickListener);
            if (callout != null && callout.getImage() != null) {
                EntitySelectCalloutSetup.setupImageLayout(activity, preHoneycombBarcodeButton, callout.getImage());
            }
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
        if (!isUsingActionBar()) {
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

    /**
     * Checks if this activity uses the ActionBar
     */
    private boolean isUsingActionBar() {
        return searchView != null;
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

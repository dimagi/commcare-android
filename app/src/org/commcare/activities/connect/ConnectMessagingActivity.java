package org.commcare.activities.connect;

import android.app.SearchManager;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.fragments.connectMessaging.ConnectMessageChannelListFragment;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;

public class ConnectMessagingActivity extends CommCareActivity<ConnectMessagingActivity> {
    public static final String CCC_MESSAGE = "ccc_message";

    public NavController controller;
    NavController.OnDestinationChangedListener destinationListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_messaging);
        setTitle("Messaging");
        Window window = getWindow();
        window.setStatusBarColor(getResources().getColor(R.color.connect_status_bar_color));
        ColorDrawable colorDrawable = new ColorDrawable(getResources().getColor(R.color.connect_blue_color));
        getSupportActionBar().setBackgroundDrawable(colorDrawable);

        destinationListener = FirebaseAnalyticsUtil.getDestinationChangeListener();

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect_messaging);
        controller = navHostFragment.getNavController();
        controller.addOnDestinationChangedListener(destinationListener);
        NavigationUI.setupActionBarWithNavController(this, controller);

        String action = getIntent().getStringExtra("action");
        if(action != null) {
            handleRedirect(action);
        }
    }

    @Override
    protected void onDestroy() {
        if (destinationListener != null) {
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_connect_messaging);
            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();
                navController.removeOnDestinationChangedListener(destinationListener);
            }
            destinationListener = null;
        }

        super.onDestroy();
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        getSupportActionBar().setTitle(title);
    }

    private void handleRedirect(String action) {
        if(action.equals(CCC_MESSAGE)) {
            ConnectManager.init(this);
            ConnectManager.unlockConnect(this, success -> {
                if (success) {
                    String channelId = getIntent().getStringExtra(
                            ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID);
                    ConnectMessagingChannelRecord channel = ConnectDatabaseHelper.getMessagingChannel(this, channelId);

                    int fragmentId = channel.getConsented() ? R.id.connectMessageFragment : R.id.channelListFragment;

                    Bundle bundle = new Bundle();
                    bundle.putString("channel_id", channelId);

                    NavOptions options = new NavOptions.Builder()
                            .setPopUpTo(controller.getGraph().getStartDestinationId(), true)
                            .build();
                    controller.navigate(fragmentId, bundle, options);
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connect_messaging, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);

        if (searchItem != null) {
            SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

            searchView.setOnCloseListener(() -> false);

            EditText searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            searchPlate.setHint("Search");

            View searchPlateView = searchView.findViewById(androidx.appcompat.R.id.search_plate);
            searchPlateView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    // Do your logic here
                    Toast.makeText(getApplicationContext(), query, Toast.LENGTH_SHORT).show();
                    sendSearchQueryToFragment(query);
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
                }
            });

            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        }

        return super.onCreateOptionsMenu(menu);
    }

    public void sendSearchQueryToFragment(String query) {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect_messaging);
        if (navHostFragment != null) {
            Fragment currentFragment = navHostFragment.getChildFragmentManager().getFragments().get(0);
            if (currentFragment instanceof ConnectMessageChannelListFragment) {
                ((ConnectMessageChannelListFragment) currentFragment).onSearchQueryReceived(query);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return switch (id) {
            case R.id.action_notification -> true;
            default -> super.onOptionsItemSelected(item);
        };
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect_messaging);
        return navHostFragment.getNavController().navigateUp() || super.onSupportNavigateUp();
    }
}

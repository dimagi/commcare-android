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
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import org.commcare.dalvik.R;
import org.commcare.fragments.connectMessaging.ChannelListFragment;

public class ConnectMessagingActivity extends AppCompatActivity {

    public NavController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_messaging);
        Window window = getWindow();
        window.setStatusBarColor(getResources().getColor(R.color.connect_status_bar_color));
        ColorDrawable colorDrawable = new ColorDrawable(getResources().getColor(R.color.connect_blue_color));
        getSupportActionBar().setBackgroundDrawable(colorDrawable);
        setTitle("Messaging");
         getSupportActionBar().setTitle("Messaging");

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect_messaging);
        controller = navHostFragment.getNavController();
        NavigationUI.setupActionBarWithNavController(this, controller);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connect_messaging, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);

        if (searchItem != null) {
            SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

            searchView.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    return false;
                }
            });

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
            if (currentFragment instanceof ChannelListFragment) {
                ((ChannelListFragment) currentFragment).onSearchQueryReceived(query);
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

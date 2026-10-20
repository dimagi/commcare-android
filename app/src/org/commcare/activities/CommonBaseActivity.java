package org.commcare.activities;

import android.os.Build;
import android.os.Bundle;
import android.view.Menu;

import org.commcare.utils.AndroidUtil;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

public class CommonBaseActivity extends AppCompatActivity {

    private static final String KEY_MENU_OPEN = "cc_menu_open";
    private boolean wasOptionsMenuOpen = false;
    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        androidx.appcompat.widget.Toolbar toolbar = findViewById(org.commcare.dalvik.R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(true);

            AndroidUtil.attachWindowInsetsListener(this, android.R.id.content);
        }
    }

    /**
     * Sets the action bar title with no subtitle, clearing any subtitle a previous page set so the
     * bar keeps its single-line appearance.
     */
    public void setActionBarTitle(@Nullable CharSequence title) {
        setActionBarTitle(title, null);
    }

    /**
     * Sets the action bar title and an optional second line. Pass a {@code null} subtitle for the
     * standard single-line bar; a non-null subtitle renders below the title.
     */
    public void setActionBarTitle(@Nullable CharSequence title, @Nullable CharSequence subtitle) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
            actionBar.setSubtitle(subtitle);
        }
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        wasOptionsMenuOpen = true;
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        wasOptionsMenuOpen = false;
        super.onPanelClosed(featureId, menu);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_MENU_OPEN, wasOptionsMenuOpen);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        wasOptionsMenuOpen = savedInstanceState.getBoolean(KEY_MENU_OPEN, false);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (wasOptionsMenuOpen) {
            wasOptionsMenuOpen = false;
            getWindow().getDecorView().post(this::openOptionsMenu);
        }
        return super.onPrepareOptionsMenu(menu);
    }
}

package org.commcare.activities;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.commcare.preferences.DevSessionRestorer;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.provider.TestLatestBuildReceiver;
import org.javarosa.core.services.locale.Localization;

/**
 * Any CC activity from which we want the user to be able to save the current session via an item
 * in the options menu should subclass this class
 *
 * @author amstone
 */
public abstract class SaveSessionCommCareActivity<R> extends SessionAwareCommCareActivity<R> {

    private static final int MENU_SAVE_SESSION = Menu.FIRST;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_SAVE_SESSION, Menu.NONE, Localization.get("menu.save.session"));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(MENU_SAVE_SESSION).setVisible(DeveloperPreferences.isSessionSavingEnabled());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SAVE_SESSION) {
            DevSessionRestorer.saveSessionToPrefs();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == TestLatestBuildReceiver.LAUNCH_BUILD_REFRESH) {
            switch(intent.getStringExtra(RefreshToLatestBuildActivity.UPDATE_ATTEMPT_RESULT)) {
                case RefreshToLatestBuildActivity.ALREADY_UP_TO_DATE:
                    Toast.makeText(this, "Your app is already up to date, so no refresh occurred",
                            Toast.LENGTH_LONG).show();
                    return;
                case RefreshToLatestBuildActivity.UPDATE_ERROR:
                    Toast.makeText(this, "An error occurred while attempting to update, so no " +
                            "refresh occurred", Toast.LENGTH_LONG).show();
                    return;
            }
        }
    }

}

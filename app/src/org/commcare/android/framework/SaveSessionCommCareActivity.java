package org.commcare.android.framework;

import android.view.Menu;
import android.view.MenuItem;

import org.commcare.android.session.DevSessionRestorer;
import org.javarosa.core.services.locale.Localization;

/**
 * Any CC activity from which we want the user to be able to save the current session via an item
 * in the options menu should subclass this class
 *
 * @author amstone
 */
public abstract class SaveSessionCommCareActivity<R> extends SessionAwareCommCareActivity<R> {

    // Choose a high number so that it will be higher than the menu ids of all other menu items
    // created by subclasses
    private static final int MENU_SAVE_SESSION = 9999;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_SAVE_SESSION, Menu.NONE, Localization.get("menu.save.session"));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SAVE_SESSION) {
            DevSessionRestorer.saveSessionToPrefs();
        }
        return super.onOptionsItemSelected(item);
    }

}

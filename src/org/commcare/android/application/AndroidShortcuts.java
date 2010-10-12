/**
 * 
 */
package org.commcare.android.application;

import java.util.ArrayList;

import org.commcare.android.R;
import org.commcare.android.activities.CommCareHomeActivity;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCareSession;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

/**
 * @author ctsims
 *
 */
public class AndroidShortcuts extends Activity {

    public static final String EXTRA_KEY_SHORTCUT = "org.commcare.android.application.shortcut.command";

    String[] commands;
    String[] names;
    
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        //The Android needs to know what shortcuts are available, generate the list
        if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
        	buildMenuList();
        }
    }
    
    private void buildMenuList() {
    	ArrayList<String> names = new ArrayList<String>();
    	ArrayList<String> commands = new ArrayList<String>();
    	
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	
        builder.setTitle("CommCare ODK Shortcuts");
       

        for(Suite s : CommCareApplication._().getCommCarePlatform().getInstalledSuites()) {
        	for(org.commcare.suite.model.Menu m : s.getMenus()) {
        		if("root".equals(m.getRoot())) {
        			String name = m.getName().evaluate();
        			names.add(name);
        			commands.add(m.getId());
        		}
        	}
        }
        this.names = names.toArray(new String[0]);
        this.commands = commands.toArray(new String[0]);

        builder.setItems(this.names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                returnShortcut(AndroidShortcuts.this.names[item], AndroidShortcuts.this.commands[item]);
            }
        });

        
        AlertDialog alert = builder.create();
        alert.show();
    }
    
    /**
     * 
     */
    private void returnShortcut(String name, String command) {
        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClassName(this, CommCareHomeActivity.class.getName());
        shortcutIntent.putExtra(EXTRA_KEY_SHORTCUT, command);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this,  R.drawable.icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        // Now, return the result to the launcher

        setResult(RESULT_OK, intent);
        finish();
        return;
    }
}

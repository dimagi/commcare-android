package org.commcare.dalvik.application;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;

import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.dialogs.DialogChoiceItem;
import org.commcare.dalvik.dialogs.PaneledChoiceDialog;
import org.commcare.suite.model.Suite;

import java.util.ArrayList;

/**
 * @author ctsims
 *
 */
public class AndroidShortcuts extends Activity {

    public static final String EXTRA_KEY_SHORTCUT = "org.commcare.dalvik.application.shortcut.command";

    private String[] commands;
    private String[] names;
    
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        // The Android needs to know what shortcuts are available, generate the list
        if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            buildMenuList();
        }
    }

    private void buildMenuList() {
        final PaneledChoiceDialog dialog = new PaneledChoiceDialog(this, "Select CommCare Shortcut");
        dialog.setChoiceItems(getChoiceItemList(dialog));
        dialog.setOnCancelListener(new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                AndroidShortcuts sc = AndroidShortcuts.this;
                sc.setResult(RESULT_CANCELED);
                sc.finish();
            }
        });
        dialog.show();
    }
    
    private DialogChoiceItem[] getChoiceItemList(final PaneledChoiceDialog dialog) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> commands = new ArrayList<>();
        for (Suite s : CommCareApplication._().getCommCarePlatform().getInstalledSuites()) {
            for (org.commcare.suite.model.Menu m : s.getMenus()) {
                if ("root".equals(m.getRoot())) {
                    String name = m.getName().evaluate();
                    names.add(name);
                    commands.add(m.getId());
                }
            }
        }
        this.names = names.toArray(new String[names.size()]);
        this.commands = commands.toArray(new String[commands.size()]);

        DialogChoiceItem[] choiceItems = new DialogChoiceItem[names.size()];
        for (int i = 0; i < names.size(); i++) {
            final int index = i;
            View.OnClickListener listener = new View.OnClickListener() {
                public void onClick(View v) {
                    returnShortcut(AndroidShortcuts.this.names[index],
                            AndroidShortcuts.this.commands[index]);
                    dialog.dismiss();
                }
            };
            DialogChoiceItem item = new DialogChoiceItem(names.get(i), -1, listener);
            choiceItems[i] = item;
        }
        
        return choiceItems;
    }
    
    private void returnShortcut(String name, String command) {
        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClassName(this, CommCareHomeActivity.class.getName());
        shortcutIntent.putExtra(EXTRA_KEY_SHORTCUT, command);
        
        //Home here makes the intent new every time you call it
        shortcutIntent.addCategory(Intent.CATEGORY_HOME);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this,  R.mipmap.ic_launcher);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        // Now, return the result to the launcher

        setResult(RESULT_OK, intent);
        finish();
    }
}

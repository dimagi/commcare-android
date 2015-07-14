package org.commcare.dalvik.application;

import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.AppManagerActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

public class ManagerShortcut extends Activity {

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Intent shortcutIntent = new Intent(getApplicationContext(), AppManagerActivity.class);
        shortcutIntent.addCategory(Intent.CATEGORY_HOME);
        
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "App Manager");
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this,  R.drawable.icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        
        setResult(RESULT_OK, intent);
        finish();
    }
    
}

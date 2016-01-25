package org.commcare.dalvik.application;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.AppManagerActivity;

public class ManagerShortcut extends Activity {

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Intent shortcutIntent = new Intent(getApplicationContext(), AppManagerActivity.class);
        shortcutIntent.addCategory(Intent.CATEGORY_HOME);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.manager_activity_name));
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        setResult(RESULT_OK, intent);
        finish();
    }

}

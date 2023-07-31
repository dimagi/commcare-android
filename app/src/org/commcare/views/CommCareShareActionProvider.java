package org.commcare.views;

import android.content.Context;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.ShareActionProvider;

// We are only using this to override shareActionProvider icon and should remove
// this once we swtich to AppCompat and instead provide the icon from theme.
public class CommCareShareActionProvider extends ShareActionProvider {

    public CommCareShareActionProvider(Context context) {
        super(context);
    }

    @Override
    public View onCreateActionView() {
        return null;
    }
}

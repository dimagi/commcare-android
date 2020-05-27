package org.commcare.views;

import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.view.View;
import android.widget.ShareActionProvider;

// We are only using this to override shareActionProvider icon and should remove
// this once we swtich to AppCompat and instead provide the icon from theme.
@RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CommCareShareActionProvider extends ShareActionProvider {

    public CommCareShareActionProvider(Context context) {
        super(context);
    }

    @Override
    public View onCreateActionView() {
        return null;
    }
}

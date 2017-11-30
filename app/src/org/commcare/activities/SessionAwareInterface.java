package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

/**
 * Created by amstone326 on 11/30/17.
 */

public interface SessionAwareInterface {
    void onCreateSessionSafe(Bundle savedInstanceState);
    void onResumeSessionSafe();
    void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent);
}

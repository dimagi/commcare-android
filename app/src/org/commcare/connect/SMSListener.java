package org.commcare.connect;

import android.content.Intent;

public interface SMSListener {
    void onSuccess(Intent intent);

    void onError(String message);
}

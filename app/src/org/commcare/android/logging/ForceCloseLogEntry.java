package org.commcare.android.logging;

import java.util.Date;

/**
 * Created by amstone326 on 2/11/16.
 */
public class ForceCloseLogEntry extends AndroidLogEntry {

    public ForceCloseLogEntry(Throwable exception, String stackTrace) {
        super(AndroidLogger.TYPE_FORCECLOSE, stackTrace, new Date());
    }

}

package org.commcare.utils;

import org.commcare.views.dialogs.CustomProgressDialog;

/**
 * Created by amstone326 on 5/12/16.
 */
public class ConsumerAppsUtil {

    public static CustomProgressDialog getGenericConsumerAppsProgressDialog(int taskId, boolean addProgressBar) {
        CustomProgressDialog d = CustomProgressDialog
                .newInstance("Starting Up", "Initializing your application...", taskId);
        if (addProgressBar) {
            d.addProgressBar();
        }
        return d;
    }
}

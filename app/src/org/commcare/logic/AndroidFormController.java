package org.commcare.logic;

import android.support.annotation.NonNull;

import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.views.widgets.WidgetFactory;
import org.javarosa.core.model.FormIndex;
import org.javarosa.form.api.FormController;
import org.javarosa.form.api.FormEntryController;

/**
 * Wrapper around FormController to handle Android-specific form entry actions
 */

public class AndroidFormController extends FormController implements PendingCalloutInterface {

    private FormIndex mPendingCalloutFormIndex = null;
    private boolean wasPendingCalloutCancelled;
    private FormIndex formIndexToReturnTo = null;
    private WidgetFactory mWidgetFactory;

    public AndroidFormController(FormEntryController fec, boolean readOnly) {
        super(fec, readOnly);
    }

    @Override
    public FormIndex getPendingCalloutFormIndex() {
        return mPendingCalloutFormIndex;
    }

    @Override
    public void setPendingCalloutFormIndex(FormIndex pendingCalloutFormIndex) {
        wasPendingCalloutCancelled = false;
        mPendingCalloutFormIndex = pendingCalloutFormIndex;
    }

    @Override
    public boolean wasCalloutPendingAndCancelled(@NonNull FormIndex calloutFormIndex) {
        return wasPendingCalloutCancelled && calloutFormIndex.equals(mPendingCalloutFormIndex);
    }

    @Override
    public void setPendingCalloutAsCancelled() {
        wasPendingCalloutCancelled = true;
    }

    //CTS: Added this to protect the JR internal classes, although it's not awesome that
    //this ended up in the "logic" division.
    public WidgetFactory getWidgetFactory() {
        mWidgetFactory = new WidgetFactory(mFormEntryController.getModel().getForm(), this);
        return mWidgetFactory;
    }

    /**
     * Should be used to store the current form index if we are about to perform any actions that
     * may muck with its value, but will want to be able to restore the original one later
     */
    public void storeFormIndexToReturnTo() {
        this.formIndexToReturnTo = this.getFormIndex();
    }

    public void returnToStoredIndex() {
        if (this.formIndexToReturnTo != null) {
            jumpToIndex(this.formIndexToReturnTo);
        }
        this.formIndexToReturnTo = null;
    }

    public void notifyPermission(String permission, boolean permissionGranted) {
        if (mWidgetFactory != null) {
            mWidgetFactory.notifyPermission(permission, permissionGranted);
        }
    }
}

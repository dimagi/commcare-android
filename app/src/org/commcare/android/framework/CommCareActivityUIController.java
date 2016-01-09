package org.commcare.android.framework;

/**
 * Interface to be implemented by any class acting as a ui controller for a CommCareActivity
 *
 * IMPORTANT: Any CommCareActivity that uses a CommCareActivityUIController must override
 * CommCareActivity.initUIController() and CommCareActivity.getUIController()
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public interface CommCareActivityUIController {

    void setupUI();
    void refreshView();

}

package org.commcare.interfaces;

/**
 * Interface to be implemented by any class acting as a ui controller for a CommCareActivity
 *
 * IMPORTANT: Any CommCareActivity that uses a CommCareActivityUIController must implement the
 * WithUIController interface
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public interface CommCareActivityUIController {

    void setupUI();

    void refreshView();

}

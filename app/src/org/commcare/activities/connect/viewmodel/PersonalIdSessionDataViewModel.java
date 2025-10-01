package org.commcare.activities.connect.viewmodel;

import org.commcare.android.database.connect.models.PersonalIdSessionData;

import androidx.lifecycle.ViewModel;

public class PersonalIdSessionDataViewModel extends ViewModel {
    private PersonalIdSessionData personalIdSessionData;

    /**
     * Sets the session data instance.
     * @param sessionData a populated PersonalIdSessionData object
     */
    public void setPersonalIdSessionData(PersonalIdSessionData sessionData) {
        this.personalIdSessionData = sessionData;
    }

    /**
     * Retrieves the current session data.
     * @return the PersonalIdSessionData instance
     */
    public PersonalIdSessionData getPersonalIdSessionData() {
        return personalIdSessionData;
    }
}

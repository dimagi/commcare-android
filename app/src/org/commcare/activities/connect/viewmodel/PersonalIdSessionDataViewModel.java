package org.commcare.activities.connect.viewmodel;

import org.commcare.android.database.connect.models.PersonalIdSessionData;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

public class PersonalIdSessionDataViewModel extends ViewModel {
    private static final String PERSONAL_ID_SESSION_DATA_KEY = "PERSONAL_ID_SESSION_DATA_KEY";
    private final SavedStateHandle savedStateHandle;

    public PersonalIdSessionDataViewModel(SavedStateHandle savedStateHandle) {
        this.savedStateHandle = savedStateHandle;
    }

    /**
     * Sets the session data instance.
     * @param sessionData a populated PersonalIdSessionData object
     */
    public void setPersonalIdSessionData(PersonalIdSessionData sessionData) {
        savedStateHandle.set(PERSONAL_ID_SESSION_DATA_KEY, sessionData);
    }

    /**
     * Retrieves the current session data.
     * @return the PersonalIdSessionData instance
     */
    public PersonalIdSessionData getPersonalIdSessionData() {
        return savedStateHandle.get(PERSONAL_ID_SESSION_DATA_KEY);
    }
}

package org.commcare.activities.connect.viewmodel;

import org.commcare.android.database.connect.models.PersonalIdSessionData;

import androidx.lifecycle.ViewModel;

public class PersonalIdSessionDataViewModel extends ViewModel {
    private final PersonalIdSessionData personalIdSessionData = new PersonalIdSessionData();

    public PersonalIdSessionData getPersonalIdSessionData() {
        return personalIdSessionData;
    }
}

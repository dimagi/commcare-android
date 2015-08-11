package org.commcare.android.database;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.api.interfaces.UserDataInterface;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.model.Case;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.User;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;

/**
 * Created by wpride1 on 8/11/15.
 */
public class AndroidSandbox implements UserDataInterface {

    CommCareApplication app;

    public AndroidSandbox(CommCareApplication app){
        this.app = app;
    }

    @Override
    public IStorageUtilityIndexed<Case> getCaseStorage() {
        return app.getUserStorage(ACase.STORAGE_KEY, ACase.class);
    }

    @Override
    public IStorageUtilityIndexed<Ledger> getLedgerStorage() {
        return app.getUserStorage(Ledger.STORAGE_KEY, Ledger.class);
    }

    @Override
    public IStorageUtilityIndexed<User> getUserStorage() {
        return app.getUserStorage(User.class);
    }

    @Override
    public IStorageUtilityIndexed<FormInstance> getUserFixtureStorage() {
        return app.getUserStorage(FormRecord.class);
    }

    @Override
    public IStorageUtilityIndexed<FormInstance> getAppFixtureStorage() {
        return app.getAppStorage(FormRecord.class);
    }

    @Override
    public User getLoggedInUser() {
        try {
            return app.getSession().getLoggedInUser();
        } catch (SessionUnavailableException e) {
            return null;
        }
    }

    @Override
    public void setLoggedInUser(User user) {

    }

    @Override
    public void setSyncToken(String syncToken) {

    }

    @Override
    public void updateLastSync() {

    }
}

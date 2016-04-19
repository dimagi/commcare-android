package org.commcare.models.database;

import org.commcare.CommCareApplication;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.model.Case;
import org.commcare.core.interfaces.UserSandbox;
import org.commcare.android.database.user.models.ACase;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;

/**
 * Basically just a useful abstraction that allows us to use an Android
 * CommCareApplication as a UserDataInterface
 *
 * Created by wpride1 on 8/11/15.
 */
public class AndroidSandbox extends UserSandbox {

    private final CommCareApplication app;

    public AndroidSandbox(CommCareApplication app) {
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
        return app.getUserStorage("USER", User.class);
    }

    @Override
    public IStorageUtilityIndexed<FormInstance> getUserFixtureStorage() {
        return app.getFileBackedUserStorage("fixture", FormInstance.class);
    }

    @Override
    public IStorageUtilityIndexed<FormInstance> getAppFixtureStorage() {
        return app.getFileBackedAppStorage("fixture", FormInstance.class);
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
}

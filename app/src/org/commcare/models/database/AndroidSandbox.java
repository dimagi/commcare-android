package org.commcare.models.database;

import android.content.ContentValues;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.model.Case;
import org.commcare.cases.model.StorageBackedModel;
import org.commcare.core.interfaces.UserSandbox;
import org.commcare.android.database.user.models.ACase;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.modern.database.TableBuilder;
import org.commcare.modern.util.Pair;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.Persistable;

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
    public IStorageUtilityIndexed<StorageBackedModel> getFlatFixtureStorage(String fixtureName,
                                                                            Persistable exampleEntry) {
        String tableName = StorageBackedModel.STORAGE_KEY + TableBuilder.cleanTableName(fixtureName);
        if (exampleEntry != null) {
            DatabaseUserOpenHelper.buildTable(app.getUserDbHandle(), tableName, exampleEntry);
        }
        return app.getUserStorage(tableName, StorageBackedModel.class);
    }

    @Override
    public Pair<String, String> getFlatFixturePathBases(String fixtureName) {
        SQLiteDatabase db = app.getUserDbHandle();
        Cursor c = db.query(DbUtil.FLAT_FIXTURE_INDEX_TABLE,
                new String[]{DbUtil.FLAT_FIXTURE_INDEX_COL_BASE, DbUtil.FLAT_FIXTURE_INDEX_COL_CHILD},
                DbUtil.FLAT_FIXTURE_INDEX_COL_NAME + "=?", new String[]{fixtureName}, null, null, null);
        try {
            if (c.getCount() == 0) {
                throw new RuntimeException("no entry for " + fixtureName);
            }
            c.moveToFirst();
            return Pair.create(
                    c.getString(c.getColumnIndexOrThrow(DbUtil.FLAT_FIXTURE_INDEX_COL_BASE)),
                    c.getString(c.getColumnIndexOrThrow(DbUtil.FLAT_FIXTURE_INDEX_COL_CHILD)));
        } finally {
            c.close();
        }
    }

    @Override
    public void setFlatFixturePathBases(String fixtureName, String baseName, String childName) {
        SQLiteDatabase db = app.getUserDbHandle();
        ContentValues contentValues = new ContentValues();
        contentValues.put(DbUtil.FLAT_FIXTURE_INDEX_COL_BASE, baseName);
        contentValues.put(DbUtil.FLAT_FIXTURE_INDEX_COL_CHILD, childName);
        contentValues.put(DbUtil.FLAT_FIXTURE_INDEX_COL_NAME, fixtureName);

        try {
            db.beginTransaction();

            long ret = db.insertOrThrow(DbUtil.FLAT_FIXTURE_INDEX_TABLE,
                    DbUtil.FLAT_FIXTURE_INDEX_COL_BASE,
                    contentValues);

            if (ret > Integer.MAX_VALUE) {
                throw new RuntimeException("Waaaaaaaaaay too many values");
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
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

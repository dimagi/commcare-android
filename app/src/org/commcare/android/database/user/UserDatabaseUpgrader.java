package org.commcare.android.database.user;

import android.content.Context;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.AndroidTableBuilder;
import org.commcare.android.database.ConcreteAndroidDbHelper;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.SqlStorageIterator;
import org.commcare.android.database.app.DatabaseAppOpenHelper;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.ACasePreV6Model;
import org.commcare.android.database.user.models.AUser;
import org.commcare.android.database.user.models.CaseIndexTable;
import org.commcare.android.database.user.models.EntityStorageCache;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.cases.ledger.Ledger;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.User;
import org.javarosa.core.services.storage.Persistable;

/**
 * @author ctsims
 */
class UserDatabaseUpgrader {
    private static final String TAG = UserDatabaseUpgrader.class.getSimpleName();

    private boolean inSenseMode = false;
    private final Context c;

    public UserDatabaseUpgrader(Context c, boolean inSenseMode) {
        this.inSenseMode = inSenseMode;
        this.c = c;
    }

    public void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            if (upgradeOneTwo(db, oldVersion, newVersion)) {
                oldVersion = 2;
            }
        }

        if (oldVersion == 2) {
            if (upgradeTwoThree(db, oldVersion, newVersion)) {
                oldVersion = 3;
            }
        }

        if (oldVersion == 3) {
            if (upgradeThreeFour(db, oldVersion, newVersion)) {
                oldVersion = 4;
            }
        }

        if (oldVersion == 4) {
            if (upgradeFourFive(db, oldVersion, newVersion)) {
                oldVersion = 5;
            }
        }

        if (oldVersion == 5) {
            if (upgradeFiveSix(db, oldVersion, newVersion)) {
                oldVersion = 6;
            }
        }

        if (oldVersion == 6) {
            if (upgradeSixSeven(db, oldVersion, newVersion)) {
                oldVersion = 7;
            }
        }

        if(oldVersion == 7) {
            if(upgradeSevenEight(db, oldVersion, newVersion)) {
                oldVersion = 8;
            }
        }
    }

    private boolean upgradeOneTwo(final SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            markSenseIncompleteUnsent(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeTwoThree(final SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            markSenseIncompleteUnsent(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeThreeFour(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            addStockTable(db);
            updateIndexes(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFourFive(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("ledger_entity_id", "ledger", "entity_id"));
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFiveSix(SQLiteDatabase db, int oldVersion, int newVersion) {
        //On some devices this process takes a significant amount of time (sorry!) we should
        //tell the service to wait longer to make sure this can finish.
        CommCareApplication._().setCustomServiceBindTimeout(60 * 5 * 1000);

        db.beginTransaction();
        try {
            db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_status_open_index", "AndroidCase", "case_type,case_status"));

            DbUtil.createNumbersTable(db);
            db.execSQL(EntityStorageCache.getTableDefinition());
            EntityStorageCache.createIndexes(db);

            db.execSQL(CaseIndexTable.getTableDefinition());
            CaseIndexTable.createIndexes(db);
            CaseIndexTable cit = new CaseIndexTable(db);

            //NOTE: Need to use the PreV6 case model any time we manipulate cases in this model for upgraders
            //below 6
            SqlStorage<ACase> caseStorage = new SqlStorage<ACase>(ACase.STORAGE_KEY, ACasePreV6Model.class, new ConcreteAndroidDbHelper(c, db));

            for (ACase c : caseStorage) {
                cit.indexCase(c);
            }


            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeSixSeven(SQLiteDatabase db, int oldVersion, int newVersion) {
        //On some devices this process takes a significant amount of time (sorry!) we should
        //tell the service to wait longer to make sure this can finish.
        CommCareApplication._().setCustomServiceBindTimeout(60 * 5 * 1000);

        long start = System.currentTimeMillis();
        db.beginTransaction();
        try {
            SqlStorage<ACase> caseStorage = new SqlStorage<ACase>(ACase.STORAGE_KEY, ACasePreV6Model.class, new ConcreteAndroidDbHelper(c, db));
            updateModels(caseStorage);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
            Log.d(TAG, "Case model update complete in " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    /**
     * Depcrecate the old AUser object so that both platforms are using the User object
     * to represents users
     */
    private boolean upgradeSevenEight(SQLiteDatabase db, int oldVersion, int newVersion) {
        //On some devices this process takes a significant amount of time (sorry!) we should
        //tell the service to wait longer to make sure this can finish.
        CommCareApplication._().setCustomServiceBindTimeout(60 * 5 * 1000);
        long start = System.currentTimeMillis();
        db.beginTransaction();
        try {
            SqlStorage<Persistable> userStorage = new SqlStorage<Persistable>(AUser.STORAGE_KEY, AUser.class, new ConcreteAndroidDbHelper(c, db));
            SqlStorageIterator<Persistable> iterator = userStorage.iterate();
            while(iterator.hasMore()){
                AUser oldUser = (AUser)iterator.next();
                User newUser = oldUser.toNewUser();
                userStorage.write(newUser);
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
            Log.d(TAG, "Case model update complete in " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    private void updateIndexes(SQLiteDatabase db) {
        db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_id_index", "AndroidCase", "case_id"));
        db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_type_index", "AndroidCase", "case_type"));
        db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_status_index", "AndroidCase", "case_status"));
    }

    private void addStockTable(SQLiteDatabase db) {
        AndroidTableBuilder builder = new AndroidTableBuilder(Ledger.STORAGE_KEY);
        builder.addData(new Ledger());
        builder.setUnique(Ledger.INDEX_ENTITY_ID);
        db.execSQL(builder.getTableCreateString());
    }

    private void markSenseIncompleteUnsent(final SQLiteDatabase db) {
        //Fix for Bug in 2.7.0/1, forms in sense mode weren't being properly marked as complete after entry.
        if (inSenseMode) {

            //Get form record storage
            SqlStorage<FormRecord> storage = new SqlStorage<>(FormRecord.STORAGE_KEY, FormRecord.class, new ConcreteAndroidDbHelper(c, db));

            //Iterate through all forms currently saved
            for (FormRecord record : storage) {
                //Update forms marked as incomplete with the appropriate status
                if (FormRecord.STATUS_INCOMPLETE.equals(record.getStatus())) {
                    //update to complete to process/send.
                    storage.write(record.updateInstanceAndStatus(record.getInstanceURI().toString(), FormRecord.STATUS_COMPLETE));
                }
            }
        }
    }

    /**
     * Reads and rewrites all of the records in a table, generally to adapt an old serialization format to a new
     * format
     */
    private <T extends Persistable> void updateModels(SqlStorage<T> storage) {
        for (T t : storage) {
            storage.write(t);
        }
    }
}

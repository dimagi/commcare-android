package org.commcare.models.database.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.app.models.FormDefRecordV12;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecordV2;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectJobRecordV2;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.database.TableBuilder;
import org.commcare.resources.model.Resource;
import org.javarosa.core.services.storage.Persistable;

public class ConnectDatabaseUpgrader {
    private final Context c;

    public ConnectDatabaseUpgrader(Context c) {
        this.c = c;
    }

    public void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            if (upgradeOneTwo(db)) {
                oldVersion = 2;
            }
        }

        if (oldVersion == 2) {
            if (upgradeTwoThree(db)) {
                oldVersion = 3;
            }
        }
    }

    private boolean upgradeOneTwo(SQLiteDatabase db) {
        return addTableForNewModel(db, ConnectJobRecord.STORAGE_KEY, new ConnectJobRecord()) &&
                addTableForNewModel(db, ConnectAppRecord.STORAGE_KEY, new ConnectAppRecord()) &&
                addTableForNewModel(db, ConnectLearnModuleSummaryRecord.STORAGE_KEY, new ConnectLearnModuleSummaryRecord()) &&
                addTableForNewModel(db, ConnectJobDeliveryRecord.STORAGE_KEY, new ConnectJobDeliveryRecord()) &&
                addTableForNewModel(db, ConnectJobLearningRecord.STORAGE_KEY, new ConnectJobLearningRecord()) &&
                addTableForNewModel(db, ConnectJobAssessmentRecord.STORAGE_KEY, new ConnectJobAssessmentRecord()) &&
                addTableForNewModel(db, ConnectJobPaymentRecord.STORAGE_KEY, new ConnectJobPaymentRecord());
    }

    private boolean upgradeTwoThree(SQLiteDatabase db) {
        db.beginTransaction();

        try {
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.META_CLAIM_DATE,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobDeliveryRecord.STORAGE_KEY,
                    ConnectJobDeliveryRecord.META_REASON,
                    "TEXT"));
            //First, migrate the old ConnectJobRecord in storage to the new version
            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecordV2.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectJobRecordV2 oldRecord = (ConnectJobRecordV2)r;
                ConnectJobRecord newRecord = ConnectJobRecord.fromV2(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            //Next, migrate the old ConnectJobDeliveryRecord in storage to the new version
            oldStorage = new SqlStorage<>(
                    ConnectJobDeliveryRecord.STORAGE_KEY,
                    ConnectJobDeliveryRecordV2.class,
                    new ConcreteAndroidDbHelper(c, db));

            newStorage = new SqlStorage<>(
                    ConnectJobDeliveryRecord.STORAGE_KEY,
                    ConnectJobDeliveryRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectJobDeliveryRecordV2 oldRecord = (ConnectJobDeliveryRecordV2)r;
                ConnectJobDeliveryRecord newRecord = ConnectJobDeliveryRecord.fromV2(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
            return true;
        } catch(Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    private static boolean addTableForNewModel(SQLiteDatabase db, String storageKey,
                                               Persistable modelToAdd) {
        db.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(storageKey);
            builder.addData(modelToAdd);
            db.execSQL(builder.getTableCreateString());

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }
}

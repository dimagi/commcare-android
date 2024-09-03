package org.commcare.models.database.connect;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecordV2;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecordV3;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectJobRecordV2;
import org.commcare.android.database.connect.models.ConnectJobRecordV4;
import org.commcare.android.database.connect.models.ConnectJobRecordV7;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecordV3;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecordV8;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.ConnectUserRecordV5;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.database.TableBuilder;
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

        if (oldVersion == 3) {
            if (upgradeThreeFour(db)) {
                oldVersion = 4;
            }
        }

        if (oldVersion == 4) {
            if (upgradeFourFive(db)) {
                oldVersion = 5;
            }
        }

        if (oldVersion == 5) {
            if (upgradeFiveSix(db)) {
                oldVersion = 6;
            }
        }

        if (oldVersion == 6) {
            if (upgradeSixSeven(db)) {
                oldVersion = 7;
            }
        }

        if (oldVersion == 7) {
            if (upgradeSevenEight(db)) {
                oldVersion = 8;
            }
        }

        if (oldVersion == 8) {
            if (upgradeEightNine(db)) {
                oldVersion = 9;
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
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeThreeFour(SQLiteDatabase db) {
        db.beginTransaction();

        try {
            //First, migrate the old ConnectLinkedAppRecord in storage to the new version
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.META_CONNECTID_LINKED,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.META_OFFERED_1,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.META_OFFERED_1_DATE,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.META_OFFERED_2,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.META_OFFERED_2_DATE,
                    "TEXT"));

            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecordV3.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectLinkedAppRecordV3 oldRecord = (ConnectLinkedAppRecordV3)r;
                ConnectLinkedAppRecord newRecord = ConnectLinkedAppRecord.fromV3(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            //Next, migrate the old ConnectJobPaymentRecord in storage to the new version
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobPaymentRecord.STORAGE_KEY,
                    ConnectJobPaymentRecord.META_PAYMENT_ID,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobPaymentRecord.STORAGE_KEY,
                    ConnectJobPaymentRecord.META_CONFIRMED,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobPaymentRecord.STORAGE_KEY,
                    ConnectJobPaymentRecord.META_CONFIRMED_DATE,
                    "TEXT"));

            oldStorage = new SqlStorage<>(
                    ConnectJobPaymentRecord.STORAGE_KEY,
                    ConnectJobPaymentRecordV3.class,
                    new ConcreteAndroidDbHelper(c, db));

            newStorage = new SqlStorage<>(
                    ConnectJobPaymentRecord.STORAGE_KEY,
                    ConnectJobPaymentRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectJobPaymentRecordV3 oldRecord = (ConnectJobPaymentRecordV3)r;
                ConnectJobPaymentRecord newRecord = ConnectJobPaymentRecord.fromV3(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFourFive(SQLiteDatabase db) {
        db.beginTransaction();

        try {
            //First, migrate the old ConnectJobRecord in storage to the new version
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.META_START_DATE,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.META_IS_ACTIVE,
                    "TEXT"));


            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecordV4.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectJobRecordV4 oldRecord = (ConnectJobRecordV4)r;
                ConnectJobRecord newRecord = ConnectJobRecord.fromV4(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFiveSix(SQLiteDatabase db) {
        db.beginTransaction();

        try {
            //First, migrate the old ConnectUserRecord in storage to the new version
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecord.META_PIN,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecord.META_SECONDARY_PHONE_VERIFIED,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecord.META_VERIFY_SECONDARY_PHONE_DATE,
                    "TEXT"));

            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecordV5.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecordV5.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectUserRecordV5 oldRecord = (ConnectUserRecordV5)r;
                ConnectUserRecord newRecord = ConnectUserRecord.fromV5(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeSixSeven(SQLiteDatabase db) {
        return addTableForNewModel(db, ConnectPaymentUnitRecord.STORAGE_KEY, new ConnectPaymentUnitRecord());
    }

    private boolean upgradeSevenEight(SQLiteDatabase db) {
        db.beginTransaction();

        try {
            //First, migrate the old ConnectJobRecord in storage to the new version
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.META_USER_SUSPENDED,
                    "TEXT"));


            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecordV7.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectJobRecordV7 oldRecord = (ConnectJobRecordV7)r;
                ConnectJobRecord newRecord = ConnectJobRecord.fromV7(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeEightNine(SQLiteDatabase db) {
        db.beginTransaction();

        try {
            //First, migrate the old ConnectJobRecord in storage to the new version
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.META_LOCAL_PASSPHRASE,
                    "TEXT"));


            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecordV8.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectLinkedAppRecordV8 oldRecord = (ConnectLinkedAppRecordV8)r;
                ConnectLinkedAppRecord newRecord = ConnectLinkedAppRecord.fromV8(oldRecord);
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
        } finally {
            db.endTransaction();
        }
    }
}

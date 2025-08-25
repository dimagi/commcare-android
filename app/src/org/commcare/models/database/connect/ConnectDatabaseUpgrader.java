package org.commcare.models.database.connect;

import android.content.Context;

import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryFlagRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecordV2;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecordV3;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectJobRecordV10;
import org.commcare.android.database.connect.models.ConnectJobRecordV2;
import org.commcare.android.database.connect.models.ConnectJobRecordV4;
import org.commcare.android.database.connect.models.ConnectJobRecordV7;
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecordV3;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecordV8;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecordV9;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.ConnectUserRecordV13;
import org.commcare.android.database.connect.models.ConnectUserRecordV14;
import org.commcare.android.database.connect.models.ConnectUserRecordV16;
import org.commcare.android.database.connect.models.ConnectUserRecordV5;
import org.commcare.android.database.connect.models.PersonalIdCredential;
import org.commcare.android.database.connect.models.PersonalIdCredentialV15;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.database.TableBuilder;
import org.commcare.utils.CrashUtil;
import org.javarosa.core.services.storage.Persistable;

public class ConnectDatabaseUpgrader {
    private final Context c;

    public ConnectDatabaseUpgrader(Context c) {
        this.c = c;
    }

    public void upgrade(IDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            upgradeOneTwo(db);
            oldVersion = 2;
        }

        if (oldVersion == 2) {
            upgradeTwoThree(db);
            oldVersion = 3;
        }

        if (oldVersion == 3) {
            upgradeThreeFour(db);
            oldVersion = 4;
        }

        if (oldVersion == 4) {
            upgradeFourFive(db);
            oldVersion = 5;
        }

        if (oldVersion == 5) {
            upgradeFiveSix(db);
            oldVersion = 6;
        }

        if (oldVersion == 6) {
            upgradeSixSeven(db);
            oldVersion = 7;
        }

        if (oldVersion == 7) {
            upgradeSevenEight(db);
            oldVersion = 8;
        }

        if (oldVersion == 8) {
            upgradeEightNine(db);
            oldVersion = 9;
        }

        if (oldVersion == 9) {
            upgradeNineTen(db);
            oldVersion = 10;
        }

        if (oldVersion == 10) {
            upgradeTenEleven(db);
            oldVersion = 11;
        }

        if (oldVersion == 11) {
            upgradeElevenTwelve(db);
            oldVersion = 12;
        }

        if (oldVersion == 12) {
            upgradeTwelveThirteen(db);
            oldVersion = 13;
        }

        if (oldVersion == 13) {
            upgradeThirteenFourteen(db);
            oldVersion = 14;
        }

        if (oldVersion == 14) {
            upgradeFourteenFifteen(db);
            oldVersion = 15;
        }
        if (oldVersion == 15) {
            upgradeFifteenSixteen(db);
            oldVersion = 16;
        }
        if (oldVersion == 16) {
            upgradeSixteenSeventeen(db);
            oldVersion = 17;
        }
    }

    private void upgradeOneTwo(IDatabase db) {
        addTableForNewModel(db, ConnectJobRecord.STORAGE_KEY, new ConnectJobRecordV2());
        addTableForNewModel(db, ConnectAppRecord.STORAGE_KEY, new ConnectAppRecord());
        addTableForNewModel(db, ConnectLearnModuleSummaryRecord.STORAGE_KEY, new ConnectLearnModuleSummaryRecord());
        addTableForNewModel(db, ConnectJobDeliveryRecord.STORAGE_KEY, new ConnectJobDeliveryRecordV2());
        addTableForNewModel(db, ConnectJobLearningRecord.STORAGE_KEY, new ConnectJobLearningRecord());
        addTableForNewModel(db, ConnectJobAssessmentRecord.STORAGE_KEY, new ConnectJobAssessmentRecord());
        addTableForNewModel(db, ConnectJobPaymentRecord.STORAGE_KEY, new ConnectJobPaymentRecordV3());
        addTableForNewModel(db, ConnectLinkedAppRecord.STORAGE_KEY, new ConnectLinkedAppRecordV3());
    }

    private void upgradeTwoThree(IDatabase db) {
        db.beginTransaction();

        try {
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecordV2.STORAGE_KEY,
                    ConnectJobRecordV4.META_CLAIM_DATE,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobDeliveryRecord.STORAGE_KEY,
                    ConnectJobDeliveryRecord.META_REASON,
                    "TEXT"));
            //First, migrate the old ConnectJobRecord in storage to the new version
            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectJobRecordV2.STORAGE_KEY,
                    ConnectJobRecordV2.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectJobRecordV4.STORAGE_KEY,
                    ConnectJobRecordV4.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectJobRecordV2 oldRecord = (ConnectJobRecordV2)r;
                ConnectJobRecordV4 newRecord = ConnectJobRecordV4.fromV2(oldRecord);
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
        } catch (Exception e){
            CrashUtil.log(e.getMessage());
        }
        finally {
            db.endTransaction();
        }
    }

    private void upgradeThreeFour(IDatabase db) {
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
                ConnectLinkedAppRecordV8 newRecord = ConnectLinkedAppRecordV8.fromV3(oldRecord);
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
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeFourFive(IDatabase db) {
        db.beginTransaction();

        try {
            //First, migrate the old ConnectJobRecord in storage to the new version
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecordV4.STORAGE_KEY,
                    ConnectJobRecordV7.META_START_DATE,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecordV7.STORAGE_KEY,
                    ConnectJobRecordV7.META_IS_ACTIVE,
                    "TEXT"));


            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectJobRecordV4.STORAGE_KEY,
                    ConnectJobRecordV4.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectJobRecordV7.STORAGE_KEY,
                    ConnectJobRecordV7.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectJobRecordV4 oldRecord = (ConnectJobRecordV4)r;
                ConnectJobRecordV7 newRecord = ConnectJobRecordV7.fromV4(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeFiveSix(IDatabase db) {
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
                    ConnectUserRecordV13.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectUserRecordV5 oldRecord = (ConnectUserRecordV5)r;
                ConnectUserRecordV13 newRecord = ConnectUserRecordV13.fromV5(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeSixSeven(IDatabase db) {
        addTableForNewModel(db, ConnectPaymentUnitRecord.STORAGE_KEY, new ConnectPaymentUnitRecord());
    }

    private void upgradeSevenEight(IDatabase db) {
        db.beginTransaction();

        try {
            //First, migrate the old ConnectJobRecord in storage to the new version
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecordV7.STORAGE_KEY,
                    ConnectJobRecordV10.META_USER_SUSPENDED,
                    "TEXT"));


            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectJobRecordV7.STORAGE_KEY,
                    ConnectJobRecordV7.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectJobRecordV10.STORAGE_KEY,
                    ConnectJobRecordV10.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectJobRecordV7 oldRecord = (ConnectJobRecordV7)r;
                ConnectJobRecordV10 newRecord = ConnectJobRecordV10.fromV7(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeEightNine(IDatabase db) {
        db.beginTransaction();

        try {
            //Migrate the old ConnectLinkedAppRecord in storage to the new version
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
                ConnectLinkedAppRecordV9 newRecord = ConnectLinkedAppRecordV9.fromV8(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeNineTen(IDatabase db) {
        db.beginTransaction();

        try {
            //Migrate the old ConnectLinkedAppRecord in storage to the new version
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.META_LAST_ACCESSED,
                    "TEXT"));


            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecordV9.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectLinkedAppRecord.STORAGE_KEY,
                    ConnectLinkedAppRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectLinkedAppRecordV9 oldRecord = (ConnectLinkedAppRecordV9)r;
                ConnectLinkedAppRecord newRecord = ConnectLinkedAppRecord.fromV9(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeTenEleven(IDatabase db) {
        db.beginTransaction();

        try {
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.META_DAILY_START_TIME,
                    "TEXT"));

            db.execSQL(DbUtil.addColumnToTable(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.META_DAILY_FINISH_TIME,
                    "TEXT"));

            //First, migrate the old ConnectJobRecord in storage to the new version
            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectJobRecordV10.STORAGE_KEY,
                    ConnectJobRecordV10.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectJobRecordV10 oldRecord = (ConnectJobRecordV10)r;
                ConnectJobRecord newRecord = ConnectJobRecord.fromV10(oldRecord);
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
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeElevenTwelve(IDatabase db) {
        addTableForNewModel(db, ConnectMessagingChannelRecord.STORAGE_KEY, new ConnectMessagingChannelRecord());
        addTableForNewModel(db, ConnectMessagingMessageRecord.STORAGE_KEY, new ConnectMessagingMessageRecord());
    }

    private void upgradeTwelveThirteen(IDatabase db) {
        addTableForNewModel(db, ConnectJobDeliveryFlagRecord.STORAGE_KEY, new ConnectJobDeliveryFlagRecord());
    }

    private void upgradeThirteenFourteen(IDatabase db) {
        db.beginTransaction();
        try {
            SqlStorage<ConnectUserRecordV13> oldStorage = new SqlStorage<>(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecordV13.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecordV14.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (ConnectUserRecordV13 oldRecord : oldStorage) {
                ConnectUserRecordV14 newRecord = ConnectUserRecordV14.fromV13(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeFourteenFifteen(IDatabase db) {
        db.beginTransaction();
        try {
            SqlStorage<ConnectUserRecordV14> oldStorage = new SqlStorage<>(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecordV14.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecordV16.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (ConnectUserRecordV14 oldRecord : oldStorage) {
                ConnectUserRecordV16 newRecord = ConnectUserRecordV16.fromV14(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }


    private void upgradeFifteenSixteen(IDatabase db) {
        db.beginTransaction();
        try {
            SqlStorage<PersonalIdCredentialV15> oldStorage = new SqlStorage<>(
                    PersonalIdCredential.STORAGE_KEY,
                    PersonalIdCredentialV15.class,
                    new ConcreteAndroidDbHelper(c, db)
            );

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    PersonalIdCredential.STORAGE_KEY,
                    PersonalIdCredential.class,
                    new ConcreteAndroidDbHelper(c, db)
            );

            for (PersonalIdCredentialV15 oldRecord : oldStorage) {
                PersonalIdCredential newRecord = PersonalIdCredential.fromV15(oldRecord);
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeSixteenSeventeen(IDatabase db) {
        db.beginTransaction();
        try {

            SqlStorage<Persistable> jobStorage = new SqlStorage<>(
                    ConnectJobRecord.STORAGE_KEY,
                    ConnectJobRecord.class,
                    new ConcreteAndroidDbHelper(c, db));
            
            boolean hasConnectAccess = jobStorage.getNumRecords() > 0;

            SqlStorage<ConnectUserRecordV16> oldStorage = new SqlStorage<>(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecordV16.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectUserRecord.STORAGE_KEY,
                    ConnectUserRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (ConnectUserRecordV16 oldRecord : oldStorage) {
                ConnectUserRecord newRecord = ConnectUserRecord.fromV16(oldRecord, hasConnectAccess);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void addTableForNewModel(IDatabase db, String storageKey,
                                            Persistable modelToAdd) {
        db.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(storageKey);
            builder.addData(modelToAdd);
            db.execSQL(builder.getTableCreateString());

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}

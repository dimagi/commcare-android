package org.commcare.xml;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.cases.instance.FixtureIndexSchema;
import org.commcare.cases.model.StorageIndexedTreeElementModel;
import org.commcare.core.interfaces.UserSandbox;
import org.kxml2.io.KXmlParser;

import java.io.IOException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */

public class AndroidFlatFixtureXmlParser extends FlatFixtureXmlParser {

    private boolean isFirstCommit = true;

    public AndroidFlatFixtureXmlParser(KXmlParser parser, String fixtureName,
                                       FixtureIndexSchema schema, UserSandbox sandbox) {
        super(parser, fixtureName, schema, sandbox);
    }

    protected SQLiteDatabase getDbHandle() {
        return CommCareApplication.instance().getUserDbHandle();
    }

    @Override
    public void commit(StorageIndexedTreeElementModel entry) throws IOException {
        if (isFirstCommit) {
            SQLiteDatabase db = getDbHandle();
            db.beginTransaction();
            isFirstCommit = false;
        }
        super.commit(entry);
    }

    @Override
    public void finishProcessing(boolean wasSuccessful) {
        SQLiteDatabase db = getDbHandle();
        if (wasSuccessful) {
            db.setTransactionSuccessful();
        }
        db.endTransaction();
    }
}

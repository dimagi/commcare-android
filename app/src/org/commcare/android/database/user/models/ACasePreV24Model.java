package org.commcare.android.database.user.models;

public class ACasePreV24Model extends ACase {

    @Override
    public String[] getMetaDataFields() {
        return new String[]{INDEX_CASE_ID, INDEX_CASE_TYPE, INDEX_CASE_STATUS, INDEX_OWNER_ID};
    }

}

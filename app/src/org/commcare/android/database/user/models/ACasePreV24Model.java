package org.commcare.android.database.user.models;

import org.commcare.cases.model.CaseIndex;

public class ACasePreV24Model extends ACase {


    @Override
    public String[] getMetaDataFields() {
        return new String[]{INDEX_CASE_ID, INDEX_CASE_TYPE, INDEX_CASE_STATUS, INDEX_OWNER_ID};
    }

    @Override
    public Object getMetaData(String fieldName) {
        if (fieldName.equals(INDEX_CASE_ID)) {
            return id;
        } else if (fieldName.equals("case-type")) {
            return typeId;
        } else if (fieldName.equals(INDEX_CASE_STATUS)) {
            return closed ? "closed" : "open";
        } else if (fieldName.startsWith(INDEX_CASE_INDEX_PRE)) {
            String name = fieldName.substring(fieldName.lastIndexOf('-') + 1, fieldName.length());

            for (CaseIndex index : this.getIndices()) {
                if (index.getName().equals(name)) {
                    return index.getTarget();
                }
            }
            return "";
        } else if (fieldName.equals(INDEX_OWNER_ID)) {
            String ownerId = getUserId();
            return ownerId == null ? "" : ownerId;
        } else {
            throw new IllegalArgumentException("No metadata field " + fieldName + " in the case storage system");
        }
    }

}

package org.commcare.provider;

import android.content.ContentValues;

/**
 * Different types of insertions for InstanceProvider
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
enum InstanceProviderInsertType {
    /**
     * Instance should be linked with form record present in the session
     */
    SESSION_LINKED,
    /**
     * Instance is being (manually) imported and will attached to a form record in an indexing pass
     */
    UNINDEXED_IMPORT,
    /**
     * Instance is being moved to a new user sandbox; existing form record will be updated by migration code
     */
    SANDBOX_MIGRATED;

    static InstanceProviderInsertType getInsertionType(ContentValues values) {
        if (values.containsKey(InstanceProviderAPI.SANDBOX_MIGRATION_SUBMISSION)) {
            values.remove(InstanceProviderAPI.SANDBOX_MIGRATION_SUBMISSION);
            return InstanceProviderInsertType.SANDBOX_MIGRATED;
        } else if (values.containsKey(InstanceProviderAPI.UNINDEXED_SUBMISSION)) {
            values.remove(InstanceProviderAPI.UNINDEXED_SUBMISSION);
            return InstanceProviderInsertType.UNINDEXED_IMPORT;
        }

        return InstanceProviderInsertType.SESSION_LINKED;
    }
}

package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

@Table("connect_key")
public class ConnectKeyRecord extends Persisted {
    @Persisting(1)
    String userId;
    @Persisting(2)
    byte[] passphrase;
}

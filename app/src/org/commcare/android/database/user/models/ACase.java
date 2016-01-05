package org.commcare.android.database.user.models;

import org.commcare.cases.model.Case;
import org.commcare.modern.models.EncryptedModel;

/**
 * NOTE: All new fields should be added to the case class using the "data" class,
 * as it demonstrated by the "userid" field. This prevents problems with datatype
 * representation across versions.
 * 
 * @author Clayton Sims
 */
public class ACase extends Case implements EncryptedModel {
    public static final String STORAGE_KEY = "AndroidCase";
    
    
    public ACase() {
        super();
    }
    
    public ACase(String a, String b) {
        super(a,b);
    }
    public boolean isBlobEncrypted() {
        return true;
    }

    public boolean isEncrypted(String data) {
        if (data.equals("casetype")) {
            return true;
        } else if (data.equals("externalid")) {
            return true;
        } return false;
    }
}

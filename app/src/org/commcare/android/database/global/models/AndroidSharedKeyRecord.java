package org.commcare.android.database.global.models;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;

import org.commcare.core.encryption.CryptUtil;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.util.EncryptionKeyHelper;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.PropertyUtils;

import java.security.GeneralSecurityException;
import java.security.KeyPair;

/**
 * This is a record of a key that CommCare ODK has shared with another app
 * in order for that app to send data to CommCare securely.
 *
 * @author ctsims
 */
@Table("android_sharing_key")

public class AndroidSharedKeyRecord extends Persisted {

    public final static String META_KEY_ID = "sharing_key_id";

    @Persisting(1)
    @MetaField(value = META_KEY_ID, unique = true)
    String keyId;
    @Persisting(2)
    byte[] privateKey;
    @Persisting(3)
    byte[] publicKey;

    /*
     * Deserialization only
     */
    public AndroidSharedKeyRecord() {

    }

    public AndroidSharedKeyRecord(String keyId, byte[] privateKey, byte[] publicKey) {
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static AndroidSharedKeyRecord generateNewSharingKey()
            throws EncryptionKeyHelper.EncryptionKeyException {
        KeyPair pair = CryptUtil.generateRandomKeyPair(512);
        byte[] encodedPrivate = pair.getPrivate().getEncoded();
        byte[] encodedPublic = pair.getPublic().getEncoded();
        return new AndroidSharedKeyRecord(PropertyUtils.genUUID(), encodedPrivate, encodedPublic);
    }

    private String getKeyId() {
        return keyId;
    }

    public final static String EXTRA_KEY_ID = "commcare_sharing_key_id";
    private final static String EXTRA_KEY_PAYLOAD = "commcare_sharing_key_payload";

    public void writeResponseToIntent(Intent response) {
        response.putExtra(EXTRA_KEY_ID, this.getKeyId());
        response.putExtra(EXTRA_KEY_PAYLOAD, this.publicKey);
    }

    private final static String EXTRA_KEY_CALLOUT = "commcare_sharing_key_callout";
    private final static String EXTRA_KEY_CALLOUT_SYMETRIC_KEY = "commcare_sharing_key_symetric";

    public Bundle getIncomingCallout(Intent incoming) {
        byte[] incomingCallout = incoming.getByteArrayExtra(EXTRA_KEY_CALLOUT);
        byte[] incomingKey = incoming.getByteArrayExtra(EXTRA_KEY_CALLOUT_SYMETRIC_KEY);
        Parcel p = Parcel.obtain();
        byte[] decoded;
        try {
            byte[] aesKey = CryptUtil.decrypt(incomingKey, CryptUtil.getPrivateKeyCipher(this.privateKey));
            decoded = CryptUtil.decrypt(incomingCallout, CryptUtil.getAesKeyCipher(aesKey));
        } catch (GeneralSecurityException gse) {
            Logger.exception("Error getting incoming callout", gse);
            return null;
        }

        p.unmarshall(decoded, 0, decoded.length);
        p.setDataPosition(0);

        Bundle result = p.readBundle();
        p.recycle();
        return result;
    }
}

package org.commcare.models.database.global.models;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.models.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.MetaField;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.PropertyUtils;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

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

    public static AndroidSharedKeyRecord generateNewSharingKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(256, new SecureRandom());
            KeyPair pair = generator.genKeyPair();
            byte[] encodedPrivate = pair.getPrivate().getEncoded();
            String privateEncoding = pair.getPrivate().getFormat();
            byte[] encodedPublic = pair.getPublic().getEncoded();
            String publicencoding = pair.getPublic().getFormat();
            return new AndroidSharedKeyRecord(PropertyUtils.genUUID(), pair.getPrivate().getEncoded(), pair.getPublic().getEncoded());
        } catch (NoSuchAlgorithmException nsae) {
            return null;
        }
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
            Logger.exception(gse);
            return null;
        }

        p.unmarshall(decoded, 0, decoded.length);
        p.setDataPosition(0);

        Bundle result = p.readBundle();
        p.recycle();
        return result;
    }
}

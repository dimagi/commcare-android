package org.commcare.models.encryption;

import org.commcare.util.SignatureVerifier;

/**
 * Created by amstone326 on 4/6/16.
 */
public class AndroidProfileSignatureVerifier extends SignatureVerifier {

    @Override
    public boolean verify(String message, String signature) {
        try {
            byte[] signatureBytes = SigningUtil.getBytesFromString(signature);
            String verifiedValue = SigningUtil.verifySignatureAgainstMessage(message, signatureBytes);
            if (verifiedValue == null) {
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

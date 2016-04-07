package org.commcare.models.encryption;

import android.util.Pair;

import org.commcare.util.SignatureVerifier;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;

/**
 * Created by amstone326 on 4/6/16.
 */
public class AndroidSignedPermissionVerifier extends SignatureVerifier {

    public AndroidSignedPermissionVerifier() {

    }

    @Override
    public boolean verify(String message, String signature) {
        byte[] signatureBytes;

        // TODO: REMOVE - for testing only
        Pair<PrivateKey, PublicKey> keyPair = generateKeyPair();
        PrivateKey privateKey = keyPair.first;
        signatureBytes = generateTestSignature(message, privateKey);
        //

        try {
            //signatureBytes = SigningUtil.getBytesFromString(signature);
            PublicKey publicKey = keyPair.second;
            if (SigningUtil.verifyMessageSignature(publicKey, message, signatureBytes)) {
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // FOR TESTING ONLY
    private static Pair<PrivateKey, PublicKey> generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            keyGen.initialize(1024, random);
            KeyPair pair = keyGen.generateKeyPair();
            PrivateKey priv = pair.getPrivate();
            PublicKey pub = pair.getPublic();
            return new Pair<>(priv, pub);
        } catch (Exception e) {
            return null;
        }

    }

    // FOR TESTING ONLY
    private static byte[] generateTestSignature(String msg, PrivateKey privateKey) {
        try {
            Signature rsa = SigningUtil.getRSASignatureInstanceWithProvider();
            rsa.initSign(privateKey);
            rsa.update(SigningUtil.getBytesFromString(msg));
            return rsa.sign();
        } catch (Exception e) {
            return null;
        }
    }

}

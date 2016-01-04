package org.commcare.android.util;

import android.util.Pair;

import org.commcare.android.logic.GlobalConstants;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/**
 * A set of helper methods for verifying whether a message was genuinely sent from HQ. Currently we
 * expcect the SMS in the format [commcare app - do not delete] link where the link resolves to
 * some string such as
 *
 * Y2NhcHA6IGh0dHA6Ly9iaXQubHkvMU5JMUl6MyBzaWduYXR1cmU6IEvECygFUhiUH
 * 3TRjC0lClQrpLR7lG//IpDYpRH7ComtZRjTirteXmPyM9fRgbPZ9K6jG9zEms9WQj55Uo7jTujKNYThjU8rJJmWLouJBr/Yn
 * WobEupwzn6DP2FavPF1YLPbp0ZctOfymW3m4j3VZ0lR2dMOjmInMSBiInqICKid
 *
 * Which is base 64 decoded decoded into:
 *
 * ccapp: <profile link> signature: <binary signature>
 *
 * And we can then verify that the profile link was in fact signed (using SHA256withRSA) by
 * the CommCareHQ private key
 *
 * Created by wpride1 on 9/11/15.
 */
public class SigningUtil {

    /**
     * Given a trimmed byte[] payload, return the parsed out download link and signature
     * @return Pair of <Download Link, Signature>
     * @throws Exception Throw a generic exception if we fail during signature parse/verification
     */
    public static Pair<String, byte[]> getUrlAndSignatureFromPayload(byte[] payload) throws Exception{
        byte[] signatureBytes = getSignatureBytes(payload);
        byte[] messageBytes = getMessageBytes(payload);
        String downloadLink = getDownloadLink(messageBytes);
        return new Pair<>(downloadLink, signatureBytes);
    }


    /**
     * Given a URL, return the text at that location as a String
     */
    public static String convertUrlToPayload(String url) throws IOException {
        return readURL(url);
    }

    // given the raw trimmed byte paylaod, return the message (everything before the signature)
    public static byte[] getMessageBytes(byte[] payload){
        byte[] messageBytes = new byte[getSignatureStartIndex(payload)];
        for(int i = 0; i< getSignatureStartIndex(payload); i++){
            messageBytes[i] = payload[i];
        }
        return messageBytes;
    }

    /**
     * Given the link and signature, verify the link using the public key
     * @param message the download link
     * @param signature the signature bytes
     * @return valid download link if verified, null if not verified
     * @throws SignatureException if we have an internal error during verification
     */
    public static String verifyMessageAndBytes(String message, byte[] signature) throws Exception {
        String keyString = GlobalConstants.TRUSTED_SOURCE_PUBLIC_KEY;
        boolean success = verifyMessageSignatureHelper(keyString, message, signature);

        if(success){
            return message;
        }
        return null;
    }

    /**
     * Given the raw message bytes not including the signature, convert to UTF-8 and parse out
     * the download link
     *
     * @param messageBytes the raw bytes of the message payload (not the signature)
     * @return the parsed out profile link
     */
    public static String getDownloadLink(byte[] messageBytes) throws Exception{
        String textMessage =  new String(messageBytes, "UTF-8");
        return textMessage.substring(textMessage.indexOf("ccapp: ") + "ccapp: ".length(),
                textMessage.indexOf("signature") - 1);
    }

    /**
     * Get the byte representation of the signature from the plaintext. We have to pull this out
     * directly because the conversion from Base64 can have a non-1:1 correspondence with the actual
     * bytes
     *
     * @return the binary representation of the signtature
     */
    public static byte[] getSignatureBytes(byte[] messageBytes) {
        int lastSpaceIndex = getSignatureStartIndex(messageBytes);
        int signatureByteLength = messageBytes.length - lastSpaceIndex;
        byte[] signatureBytes = new byte[signatureByteLength];
        for(int i = 0; i< signatureByteLength; i++){
            signatureBytes[i] = messageBytes[i + lastSpaceIndex];
        }
        return signatureBytes;
    }

    /**
     * Iterate through the byte array until we find the third "space" character (represented
     * by integer 32) and then return its index
     * @param messageBytes the raw bytes of the Base64 message
     * @return index of the third "space" byte, -1 if none encountered
     */
    private static int getSignatureStartIndex(byte[] messageBytes) {
        int index = 0;
        int spaceCount = 0;
        int spaceByte = 32;
        for(byte b: messageBytes){
            if(b == spaceByte){
                if(spaceCount == 2){
                    return index + 1;
                }
                else{
                    spaceCount++;
                }
            }
            index++;
        }
        return -1;
    }

    // given a text message, return the raw Base64 bytes
    public static byte[] getBytesFromString(String stringMessage) throws Exception{
        return Base64.decode(stringMessage);
    }

    // given a text message, trim out the [commcare app - do not delete] and return
    public static String trimMessagePayload(String newMessage){
        return newMessage.substring(newMessage.indexOf(GlobalConstants.SMS_INSTALL_KEY_STRING) +
                GlobalConstants.SMS_INSTALL_KEY_STRING.length() + 1);
    }

    /**
     *
     * @param publicKeyString the known public key of CCHQ
     * @param message the message content
     * @param messageSignature the signature generated by HQ with its private key and the message content
     * @return whether or not the message was verified to be sent with HQ's private key
     */
    public static boolean verifyMessageSignatureHelper(String publicKeyString, String message, byte[] messageSignature) throws Base64DecoderException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        PublicKey publicKey = getPublicKey(publicKeyString);
        return verifyMessageSignature(publicKey, message, messageSignature);
    }
    // convert from a key string to a PublicKey object
    private static PublicKey getPublicKey(String key) throws Base64DecoderException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] derPublicKey = Base64.decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(derPublicKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    private static boolean verifyMessageSignature(PublicKey publicKey, String messageString, byte[] signature) throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        Signature sign = Signature.getInstance("SHA256withRSA/PSS", new BouncyCastleProvider());
        byte[] message = messageString.getBytes();
        sign.initVerify(publicKey);
        sign.update(message);
        return sign.verify(signature);
    }

    /**
     * Read the data from the URL arg and return as a string (only return first line)
     */
    public static String readURL(String url) throws IOException {
        String acc = "";
        URL oracle = new URL(url);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(oracle.openStream()));
        String inputLine;
        // only return the first line
        if ((inputLine = in.readLine()) != null)
            acc = inputLine;
        in.close();
        return acc;
    }
}

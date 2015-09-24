package org.commcare.android.util;

import org.commcare.android.logic.GlobalConstants;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;

/**
 * A set of helper methods for verifying whether a message was genuinely sent from HQ. Currently we
 * expcect the SMS in the format [COMMCARE APP - DO NOT DELETE] {BASE64-Encoded-Message} EG
 *
 * [COMMCARE APP - DO NOT DELETE] Y2NhcHA6IGh0dHA6Ly9iaXQubHkvMU5JMUl6MyBzaWduYXR1cmU6IEvECygFUhiUH
 * 3TRjC0lClQrpLR7lG//IpDYpRH7ComtZRjTirteXmPyM9fRgbPZ9K6jG9zEms9WQj55Uo7jTujKNYThjU8rJJmWLouJBr/Yn
 * WobEupwzn6DP2FavPF1YLPbp0ZctOfymW3m4j3VZ0lR2dMOjmInMSBiInqICKid
 *
 * WHERE BASE64-Encoded-Message is decoded into:
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
     * Retrieve the text message at the URL, then process this URL into a CommCare install message
     * and verify its authenticity
     * @param url the URL storing the CommCare encoded install message
     * @return the download link if the message was valid and verified, null otherwise
     * @throws SignatureException
     */
    public static String retrieveParseAndVerifyURL(String url) throws SignatureException, IOException {
        String text = readURL(url);
        return parseAndVerifySMS(text);
    }

    /**
     *
     * @param text the parsed out text message in the expected link/signature format
     * @return the download link if the message was valid and verified, null otherwise
     * @throws SignatureException if we discovered a valid-looking message but could not verifyMessageSignatureHelper it
     */
    public static String parseAndVerifySMS(String text) throws SignatureException {
        // parse out the app link and signature. We assume there is a space after ccapp: and
        // signature: and that the end of the signature is the end of the text content
        try {
            byte[] signatureBytes = getSignatureBytes(text);
            String decodedMessage = parseAndDecodeSMS(text);
            String downloadLink = getDownloadLink(decodedMessage);
            boolean success = verifySMS(downloadLink, signatureBytes);
            if(success){
                return downloadLink;
            }
        } catch(Exception e) {
            e.printStackTrace();
            throw new SignatureException();
        }
        return null;
    }

    /**
     * Get the byte representation of the signature from the plaintext. We have to pull this out
     * directly because the conversion from Base64 can have a non-1:1 correspondence with the actual
     * bytes
     *
     * @param textMessage
     * @return the binary representation of the signtature
     * @throws Exception if any errors are encountered
     */
    public static byte[] getSignatureBytes(String textMessage) throws Exception {
        byte[] messageBytes = getBytesFromSMS(textMessage);
        int lastSpaceIndex = getSignatureStartIndex(messageBytes);
        int signatureByteLength = messageBytes.length - lastSpaceIndex;
        byte[] signatureBytes = new byte[signatureByteLength];
        for(int i = 0; i< signatureByteLength; i++){
            signatureBytes[i] = messageBytes[i + lastSpaceIndex];
        }
        return signatureBytes;
    }

    public static int getLastSpaceIndex(String textMessage) throws Exception{
        byte[] messageBytes = getBytesFromSMS(textMessage);
        return getSignatureStartIndex(messageBytes);
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

    /**
     * @param textMessage the plain text SMS message
     * @return the parsed out profile link
     */
    public static String getDownloadLink(String textMessage){
        String downloadLink  = textMessage.substring(textMessage.indexOf("ccapp: ") + "ccapp: ".length(),
                textMessage.indexOf("signature") - 1);
        return downloadLink;
    }

    // given a text message, parse out the cruft and return the raw Base64 bytes
    public static byte[] getBytesFromSMS(String newMessage) throws Exception{
        return Base64.decode(parseMessage(newMessage));
    }
    // given a text message, parse out the cruft and return the raw String
    private static String parseAndDecodeSMS(String newMessage) throws Exception {
        String parsed = parseMessage(newMessage);
        int lastSpaceIndex = getSignatureStartIndex(getBytesFromSMS(newMessage));
        String messageString = parsed.substring(0, lastSpaceIndex);
        return decodeEncodedSMS(messageString);
    }

    // given a text message, parse out the cruft and return
    private static String parseMessage(String newMessage){
        String parsed = newMessage.substring(newMessage.indexOf(GlobalConstants.SMS_INSTALL_KEY_STRING) +
                GlobalConstants.SMS_INSTALL_KEY_STRING.length() + 1);
        return parsed;
    }

    private static boolean verifySMS(String message, byte[] signature){
        String keyString = GlobalConstants.CCHQ_PUBLIC_KEY;
        return SigningUtil.verifyMessageSignatureHelper(keyString, message, signature);
    }
    /**
     *
     * @param publicKeyString the known public key of CCHQ
     * @param message the message content
     * @param messageSignature the signature generated by HQ with its private key and the message content
     * @return whether or not the message was verified to be sent with HQ's private key
     */
    public static boolean verifyMessageSignatureHelper(String publicKeyString, String message, byte[] messageSignature) {
        try {
            PublicKey publicKey = getPublicKey(publicKeyString);
            return verifyMessageSignature(publicKey, message, messageSignature);
        } catch (Exception e) {
            // a bunch of exceptions can be thrown from the crypto methods. I mostly think we just
            // care that we couldn't verify it
            e.printStackTrace();
        }
        return false;
    }
    // convert from a key string to a PublicKey object
    private static PublicKey getPublicKey(String key) throws Exception{
        byte[] derPublicKey = Base64.decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(derPublicKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    private static boolean verifyMessageSignature(PublicKey publicKey, String messageString, byte[] signature) throws SignatureException, NoSuchAlgorithmException, Base64DecoderException, InvalidKeyException {
        Signature sign = Signature.getInstance("SHA256withRSA/PSS", new BouncyCastleProvider());
        byte[] message = messageString.getBytes();
        sign.initVerify(publicKey);
        sign.update(message);
        return sign.verify(signature);
    }

    private static String decodeEncodedSMS(String text) throws  SignatureException{
        String decodedMessage = null;
        try {
            decodedMessage = new String(Base64.decode(text), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new SignatureException();
        } catch (Base64DecoderException e) {
            e.printStackTrace();
            throw new SignatureException();
        }
        return decodedMessage;
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

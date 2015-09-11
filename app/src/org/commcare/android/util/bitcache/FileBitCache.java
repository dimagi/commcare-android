/**
 *
 */
package org.commcare.android.util.bitcache;

import android.content.Context;

import org.commcare.android.crypt.CryptUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 * @author ctsims
 */
public class FileBitCache implements BitCache {
    Context context;
    SecretKey key;
    File temp;

    protected FileBitCache(Context context) {
        this.context = context;
    }

    @Override
    public void initializeCache() throws IOException {
        File cacheLocation = context.getCacheDir();

        //generate temp file
        temp = File.createTempFile("commcare_pull_" + new Date().getTime(), "xml", cacheLocation);
        key = CryptUtil.generateSemiRandomKey();
    }

    @Override
    public OutputStream getCacheStream() throws IOException {
        //generate write key/cipher
        try {
            Cipher encrypter = Cipher.getInstance("AES");

            encrypter.init(Cipher.ENCRYPT_MODE, key);

            //stream file 
            FileOutputStream fos = new FileOutputStream(temp);
            CipherOutputStream cos = new CipherOutputStream(fos, encrypter);
            BufferedOutputStream bos = new BufferedOutputStream(cos, 1024);

            return bos;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream retrieveCache() throws IOException {
        try {
            //generate read key/cipher
            Cipher decrypter = Cipher.getInstance("AES");
            decrypter.init(Cipher.DECRYPT_MODE, key);

            //process
            FileInputStream fis = new FileInputStream(temp);
            BufferedInputStream bis = new BufferedInputStream(fis, 4096);
            CipherInputStream cis = new CipherInputStream(bis, decrypter);
            return cis;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void release() {
        key = null;
        context = null;
        temp.delete();
    }
}

package org.commcare.network;

import org.commcare.utils.FormUploadUtil;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.io.StreamsUtil.InputIOException;
import org.javarosa.core.io.StreamsUtil.OutputIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.Key;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

/**
 * @author ctsims
 */
public class EncryptedFileBody extends RequestBody {
    private final File file;
    private final Key key;
    private final String transformation;
    private final boolean isKeyFromAndroidKeyStore;
    private final MediaType contentType;

    public EncryptedFileBody(
            MediaType contentType,
            File file,
            Key key,
            String transformation,
            boolean isKeyFromAndroidKeyStore
    ) {
        this.contentType = contentType;
        this.file = file;
        this.key = key;
        this.transformation = transformation;
        this.isKeyFromAndroidKeyStore = isKeyFromAndroidKeyStore;
    }

    @Override
    public long contentLength() {
        return -1;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] iv = null;
        if (isKeyFromAndroidKeyStore) {
            int ivLength = fis.read() & 0xFF;
            iv = new byte[ivLength];
            fis.read(iv, 0, ivLength);
        }

        //The only time this can cause issues is if the body has disappeared since construction. Don't worry about that, since
        //it'll get caught when we initialize.
        Cipher cipher;
        if (!isKeyFromAndroidKeyStore) {
            cipher = FormUploadUtil.getDecryptCipher(key);
        } else {
            cipher = FormUploadUtil.getDecryptCipher(key, transformation, iv);
        }
        try (CipherInputStream cis = new CipherInputStream(fis, cipher)) {
            StreamsUtil.writeFromInputToOutputUnmanaged(cis, sink.outputStream());
        } catch (InputIOException iioe) {
            //Here we want to retain the fundamental problem of the _input_ being responsible for the issue
            //so we can differentiate between bad reads and bad network
            throw iioe;
        } catch (OutputIOException oe) {
            //We want the original exception here.
            throw oe.getWrapped();
        }
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return contentType;
    }

}

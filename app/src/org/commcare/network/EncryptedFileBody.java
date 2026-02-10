package org.commcare.network;

import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.io.StreamsUtil.InputIOException;
import org.javarosa.core.io.StreamsUtil.OutputIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

import static org.commcare.models.encryption.EncryptionIO.isEncryptedByAndroidKeyStore;

/**
 * @author ctsims
 */
public class EncryptedFileBody extends RequestBody {
    private final File file;
    private final Cipher cipher;
    private final MediaType contentType;

    public EncryptedFileBody(MediaType contentType, File file, Cipher cipher) {
        this.contentType = contentType;
        this.file = file;
        this.cipher = cipher;
    }

    @Override
    public long contentLength() {
        return -1;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        //The only time this can cause issues is if the body has disappeared since construction. Don't worry about that, since
        //it'll get caught when we initialize.
        FileInputStream fis = new FileInputStream(file);
        byte[] iv = null;
        if (isEncryptedByAndroidKeyStore) {
            int ivlength = ((byte[])fis.readNBytes(1))[0];
            iv = fis.readNBytes(ivlength);
        }
        CipherInputStream cis = new CipherInputStream(fis, cipher);

        try {
            StreamsUtil.writeFromInputToOutputUnmanaged(cis, sink.outputStream());
        } catch (InputIOException iioe) {
            //Here we want to retain the fundamental problem of the _input_ being responsible for the issue
            //so we can differentiate between bad reads and bad network
            throw iioe;
        } catch (OutputIOException oe) {
            //We want the original exception here.
            throw oe.getWrapped();
        } finally {
            cis.close();
        }
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return contentType;
    }

}

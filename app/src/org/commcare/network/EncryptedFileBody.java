package org.commcare.network;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.content.AbstractContentBody;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.io.StreamsUtil.InputIOException;
import org.javarosa.core.io.StreamsUtil.OutputIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

/**
 * @author ctsims
 */
public class EncryptedFileBody extends AbstractContentBody {
    private final File file;
    private final Cipher cipher;

    public EncryptedFileBody(File file, Cipher cipher, ContentType contentType) {
        super(contentType);
        this.file = file;
        this.cipher = cipher;
    }

    @Override
    public String getFilename() {
        return file.getName();
    }

    @Override
    public String getCharset() {
        return MIME.DEFAULT_CHARSET.name();
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public String getTransferEncoding() {
        return MIME.ENC_BINARY;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        //The only time this can cause issues is if the body has disappeared since construction. Don't worry about that, since
        //it'll get caught when we initialize.
        CipherInputStream cis = new CipherInputStream(new FileInputStream(file), cipher);

        try {
            StreamsUtil.writeFromInputToOutputUnmanaged(cis, out);
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
}

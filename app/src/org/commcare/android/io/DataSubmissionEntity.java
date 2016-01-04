package org.commcare.android.io;

import android.support.annotation.NonNull;
import android.util.Log;

import org.apache.http.entity.mime.MultipartEntity;
import org.commcare.android.tasks.DataSubmissionListener;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author ctsims
 */
public class DataSubmissionEntity extends MultipartEntity {

    private final DataSubmissionListener listener;
    private final int submissionId;
    private int attempt = 1;

    public DataSubmissionEntity(DataSubmissionListener listener, int submissionId) {
        super();
        this.listener = listener;
        this.submissionId = submissionId;
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        if (attempt != 1) {
            Log.i("commcare-transport", "Retrying submission, attempt #" + attempt);
        }
        super.writeTo(new CountingOutputStream(outstream, listener, submissionId));
        attempt++;
    }

    private class CountingOutputStream extends FilterOutputStream {

        private final DataSubmissionListener listener;
        private long transferred;
        private final int submissionId;

        public CountingOutputStream(final OutputStream out, final DataSubmissionListener listener, int submissionId) {
            super(out);
            this.listener = listener;
            this.transferred = 0;
            this.submissionId = submissionId;
        }


        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            this.transferred += len;
            if (listener != null) {
                this.listener.notifyProgress(submissionId, this.transferred);
            }
        }

        public void write(int b) throws IOException {
            out.write(b);
            this.transferred++;
            if (listener != null) {
                this.listener.notifyProgress(submissionId, this.transferred);
            }
        }
    }


}

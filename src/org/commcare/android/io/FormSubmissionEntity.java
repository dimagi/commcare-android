/**
 * 
 */
package org.commcare.android.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.entity.mime.MultipartEntity;
import org.commcare.android.tasks.FormSubmissionListener;

/**
 * @author ctsims
 *
 */
public class FormSubmissionEntity extends MultipartEntity {
	
	private FormSubmissionListener listener;
	private int submissionId;
	
	public FormSubmissionEntity(FormSubmissionListener listener, int submissionId) {
		super();
		this.listener = listener;
		this.submissionId = submissionId;
	}

	/* (non-Javadoc)
	 * @see org.apache.http.entity.mime.MultipartEntity#writeTo(java.io.OutputStream)
	 */
	@Override
	public void writeTo(OutputStream outstream) throws IOException {
		super.writeTo(new CountingOutputStream(outstream, listener, submissionId));
	}
	
	private class CountingOutputStream extends FilterOutputStream {

        private final FormSubmissionListener listener;
        private long transferred;
        private int submissionId;

        public CountingOutputStream(final OutputStream out, final FormSubmissionListener listener, int submissionId) {
            super(out);
            this.listener = listener;
            this.transferred = 0;
            this.submissionId = submissionId;
        }


        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            this.transferred += len;
            if(listener != null) {
            	this.listener.notifyProgress(submissionId, this.transferred);
            }
        }

        public void write(int b) throws IOException {
            out.write(b);
            this.transferred++;
            if(listener != null) {
            	this.listener.notifyProgress(submissionId, this.transferred);
            }
        }
    }


}

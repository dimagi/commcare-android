package org.commcare.sync;

public interface FormSubmissionProgressListener {
    void publishUpdateProgress(Long... progress);
}

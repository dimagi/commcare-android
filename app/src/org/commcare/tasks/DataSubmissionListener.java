package org.commcare.tasks;

/**
 * @author ctsims
 */
public interface DataSubmissionListener {
    void beginSubmissionProcess(int totalItems);

    void startSubmission(int itemNumber, long length);

    void notifyProgress(int itemNumber, long progress);

    void endSubmissionProcess();
}

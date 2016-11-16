package org.commcare.tasks;

/**
 * @author ctsims
 */
public interface DataSubmissionListener {
    void beginSubmissionProcess(int totalItems);

    void startSubmission(int itemNumber, long sizeOfItem);

    void notifyProgress(int itemNumber, long progress);

    void endSubmissionProcess(boolean success);

}

package org.commcare.update;

public interface UpdateProgressListener {
    void publishUpdateProgress(int complete, int total);
}

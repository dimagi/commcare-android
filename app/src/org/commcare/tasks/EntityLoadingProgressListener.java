package org.commcare.tasks;

public interface EntityLoadingProgressListener {

    void publishEntityLoadingProgress(int progress, int total);
}

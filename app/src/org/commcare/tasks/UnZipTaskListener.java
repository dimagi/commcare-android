package org.commcare.tasks;

public interface UnZipTaskListener {

    void OnUnzipSuccessful(Integer result);

    void OnUnzipFailure(String cause);

    void updateUnzipProgress(String update, int taskId);
}

package org.commcare.android.tasks;

public interface ProcessTaskListener {
	public void processTaskAllProcessed();
	public void processAndSendFinished(int result, int numSuccesses);
}

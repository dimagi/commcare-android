package org.commcare.utils;

/**
 * A time bound operation will run a synchronized operation in the background, with the confidence
 * that it will either execute within the time frame provided, or it will not fire its resolution
 * method, and will return control to the main thead after the timeout has expired.
 *
 * The operation should consist of two steps: A first step that should have no side effects
 * (run), and an optional second step (commit) that executes after the first is
 * completed. The second step will not be bounded, but is guaranteed to not execute if the first
 * step did not complete in time.
 *
 * A time bound operation can only be executed once.
 *
 * Created by ctsims on 4/4/2016.
 */
public abstract class TimeBoundOperation {

    final private long timeout;

    private Thread thread;
    private boolean hasExecuted;

    public TimeBoundOperation(long timeout) {
        this.timeout = timeout;
    }

    /**
     * The operation which will be executed within the timeout limit. Should not contain side
     * effects which will effect runtime if they execute indefinitely
     */
    protected abstract void run();

    /**
     * The optional commit step after the run executes which isn't bounded, and can have
     * side effects that will be guaranteed to run if the execution succeeds
     */
    protected void commit() {

    }

    /**
     * Executes the operation and optionally the commit step if the run step succeeds in time
     *
     * @return true if both phases of the timed operation succeeded. False if the operaiton
     * timed out and the commit step did not run.
     *
     * @throws IllegalStateException If the operation has already been executed
     */
    public synchronized boolean execute() throws IllegalStateException {
        if(hasExecuted) {
            throw new IllegalStateException("time bound operation has already executed");
        }
        hasExecuted = true;

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                TimeBoundOperation.this.run();
            }
        });

        thread.start();
        try {
            thread.join(timeout);
        }catch (InterruptedException e) {
            //Execution failed
            return false;
        };

        commit();
        return true;

    }

}

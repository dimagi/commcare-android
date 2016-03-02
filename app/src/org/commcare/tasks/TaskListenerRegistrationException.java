package org.commcare.tasks;

/**
 * Signifies an issue un/registering a task with a listening process.
 */
public class TaskListenerRegistrationException extends Exception {
    public TaskListenerRegistrationException(String msg) {
        super(msg);
    }
}

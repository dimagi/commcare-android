package org.commcare.connect.database;

/**
 * Exception thrown when the Connect database file does not exist.
 */
public class ConnectDatabaseNotFoundException extends RuntimeException {
    public ConnectDatabaseNotFoundException() {
        super("Connect database file does not exist");
    }
}

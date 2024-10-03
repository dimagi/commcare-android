package org.commcare.connect;

public class MessageManager {

    private static MessageManager manager = null;

    public static MessageManager getInstance() {
        if (manager == null) {
            manager = new MessageManager();
        }

        return manager;
    }
}

package org.commcare.tasks;

import org.commcare.network.ModernHttpRequest;
import org.commcare.tasks.templates.CommCareTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class SimpleHttpTask<R>
        extends CommCareTask<URL, Integer, InputStream, R> {

    public static final int SIMPLE_HTTP_TASK_ID = 11;

    //private final ModernHttpRequest requestor;

    public SimpleHttpTask(String username, String password) {
        taskId = SIMPLE_HTTP_TASK_ID;
        //requestor = new ModernHttpRequest(username, password);
    }

    @Override
    protected InputStream doTaskBackground(URL... urls) {
        /*
        try {
            return requestor.makeModernRequest(urls[0]);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        */
        return null;
    }

}

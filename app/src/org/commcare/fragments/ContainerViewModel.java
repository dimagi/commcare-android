package org.commcare.fragments;

import androidx.lifecycle.ViewModel;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic view model to store and retain objects through Android config changes
 * @param <T>
 */
public class ContainerViewModel<T> extends ViewModel {

    private final Map<String, Object> dataMap = new HashMap<>();

    public <T> void setData(String key, T data) {
        dataMap.put(key, data);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) dataMap.get(key);
    }
}

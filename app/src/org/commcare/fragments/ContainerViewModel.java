package org.commcare.fragments;

import androidx.lifecycle.ViewModel;

public class ContainerViewModel<T> extends ViewModel {

    private T data;

    public void setData(T data) {
        this.data = data;
    }

    public T getData() {
        return data;
    }
}

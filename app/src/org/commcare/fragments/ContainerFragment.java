package org.commcare.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;

/**
 * A general container for persisting an object across activity lifetime
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class ContainerFragment<Data> extends Fragment {
    private Data data;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    public void setData(Data data) {
        this.data = data;
    }

    public Data getData() {
        return data;
    }
}

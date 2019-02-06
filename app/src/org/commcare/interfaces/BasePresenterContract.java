package org.commcare.interfaces;

import android.os.Bundle;

public interface BasePresenterContract {
    void saveInstanceState(Bundle out);

    void loadSaveInstanceState(Bundle savedInstanceState);

    void onActivityDestroy();

    void start();
}

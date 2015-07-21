/**
 * 
 */
package org.commcare.android.util;

import android.support.annotation.NonNull;

/**
 * @author ctsims
 *
 */
public interface DelayedOperation<T> {
    @NonNull
    public T execute();
}

/**
 * 
 */
package org.commcare.android.models;

import android.support.annotation.NonNull;

import org.commcare.android.util.SessionUnavailableException;

/**
 * @author ctsims
 *
 */
public abstract class EntityFactory<T> {
    
    @NonNull
    public abstract Entity<T> getEntity(T data) throws SessionUnavailableException;

}

/**
 *
 */
package org.commcare.android.models;

import org.commcare.android.util.SessionUnavailableException;

/**
 * @author ctsims
 */
public abstract class EntityFactory<T> {

    public abstract Entity<T> getEntity(T data) throws SessionUnavailableException;

}

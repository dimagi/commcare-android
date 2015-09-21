/**
 * 
 */
package org.commcare.android.storage.framework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author ctsims
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Persisting {
    public PersistedType customType() default PersistedType.normal;
    public boolean nullable() default false;
    public int value();
}
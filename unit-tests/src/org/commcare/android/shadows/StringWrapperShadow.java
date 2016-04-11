package org.commcare.android.shadows;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Created by amstone326 on 4/11/16.
 */
@Implements(org.commcare.models.encryption.StringWrapper.class)
public class StringWrapperShadow {

    public void __constructor__() {
    }

    public StringWrapperShadow() {
    }

    @Implementation
    public byte[] wrap(byte[] bytes, String wrappingString) {
        return bytes;
    }

    @Implementation
    public byte[] unwrap(byte[] wrapped, String wrappingString) {
        return wrapped;
    }

}

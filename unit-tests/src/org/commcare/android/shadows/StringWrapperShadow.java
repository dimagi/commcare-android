package org.commcare.android.shadows;

import org.javarosa.core.services.Logger;
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
        Logger.log("f", "f");
    }

    @Implementation
    public byte[] wrapByteArrayWithString(byte[] bytes, String wrappingString) {
        return bytes;
    }

    @Implementation
    public byte[] unwrapByteArrayWithString(byte[] wrapped, String wrappingString) {
        return wrapped;
    }

}

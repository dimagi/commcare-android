package org.commcare.android.shadows;

import org.commcare.models.encryption.ByteEncrypter;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Needed because the standard JDK does not support the crypto provider that we use to perform these
 * operations. Given our lack of success in getting around this via other means (either by
 * installing the JCE or installing OpenJDK), and the fact that it is not actually necessary to
 * encrypt user passwords and keys in tests, we determined the best solution to be to simply
 * not perform the encryption in this case.
 */
@Implements(ByteEncrypter.class)
public class ByteEncrypterShadow {

    public void __constructor__() {
    }

    public ByteEncrypterShadow() {
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

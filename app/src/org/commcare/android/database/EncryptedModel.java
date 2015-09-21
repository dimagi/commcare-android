/**
 *
 */
package org.commcare.android.database;

/**
 * @author ctsims
 */
public interface EncryptedModel {
    public boolean isEncrypted(String data);

    public boolean isBlobEncrypted();
}

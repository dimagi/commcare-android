package org.commcare.interfaces;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public interface AppFilePathBuilder {
    /**
     * @return Absolute path to directory rooted in app level external storage
     */
    String fsPath(String relativeSubDir);
}

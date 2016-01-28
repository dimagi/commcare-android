package org.commcare.dalvik.application;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public interface AppFilePathBuilder {
    /**
     * @return Absolute path to directory rooted in app level external storage
     */
    String fsPath(String relativeSubDir);
}

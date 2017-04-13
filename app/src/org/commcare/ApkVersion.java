package org.commcare;

public class ApkVersion implements Comparable<ApkVersion> {

    private int majorVersion;
    private int minorVersion;
    private int pointReleaseVersion;

    public ApkVersion(String versionName) {
        String[] pieces = versionName.split(".");
        this.majorVersion = Integer.parseInt(pieces[0]);
        this.minorVersion = Integer.parseInt(pieces[1]);
        if (pieces.length > 2) {
            this.pointReleaseVersion = Integer.parseInt(pieces[2]);
        } else {
            this.pointReleaseVersion = 0;
        }
    }

    @Override
    public int compareTo(ApkVersion other) {
        int majorVersionDifferential = this.majorVersion - other.majorVersion;
        if (majorVersionDifferential != 0) {
            return majorVersionDifferential;
        } else {
            int minorVersionDifferential = this.minorVersion - other.minorVersion;
            //if (minorVersionDifferential != 0 || pointReleaseVersion == -1) {
                return minorVersionDifferential;
            //}
        }
    }
}
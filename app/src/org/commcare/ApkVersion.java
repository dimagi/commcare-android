package org.commcare;

public class ApkVersion implements Comparable<ApkVersion> {

    private int majorVersion;
    private int minorVersion;
    private int pointReleaseVersion;

    public ApkVersion(String versionName) {
        String[] pieces = versionName.split(".");
        this.majorVersion = Integer.parseInt(pieces[0]);
        this.minorVersion = Integer.parseInt(pieces[1]);
        this.pointReleaseVersion = (pieces.length > 2) ? Integer.parseInt(pieces[2]) : 0;
    }

    @Override
    public int compareTo(ApkVersion other) {
        int majorVersionDifferential = this.majorVersion - other.majorVersion;
        if (majorVersionDifferential != 0) {
            return majorVersionDifferential;
        } else {
            int minorVersionDifferential = this.minorVersion - other.minorVersion;
            if (minorVersionDifferential != 0 ) {
                return minorVersionDifferential;
            } else {
                return this.pointReleaseVersion - other.pointReleaseVersion;
            }
        }
    }
}
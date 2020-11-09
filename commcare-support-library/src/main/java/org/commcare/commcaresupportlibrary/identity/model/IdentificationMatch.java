package org.commcare.commcaresupportlibrary.identity.model;


import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

@SuppressWarnings("unused")
public class IdentificationMatch implements Parcelable, Comparable<IdentificationMatch> {

    private String guid;
    private MatchResult matchResult;

    /**
     * Empty constructor
     */
    public IdentificationMatch() {

    }

    /**
     * This constructor creates a new identification
     *
     * @param guid        A string containing the Identity Provider's guid of the match
     * @param matchResult Object representing match attributes like matching confidence and strength
     */
    public IdentificationMatch(String guid, MatchResult matchResult) {
        this.guid = guid;
        this.matchResult = matchResult;
    }

    protected IdentificationMatch(Parcel in) {
        guid = in.readString();
        matchResult = in.readParcelable(MatchResult.class.getClassLoader());
    }

    public static final Creator<IdentificationMatch> CREATOR = new Creator<IdentificationMatch>() {
        @Override
        public IdentificationMatch createFromParcel(Parcel in) {
            return new IdentificationMatch(in);
        }

        @Override
        public IdentificationMatch[] newArray(int size) {
            return new IdentificationMatch[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.guid);
        dest.writeParcelable(matchResult, flags);
    }

    public String getGuid() {
        return guid;
    }

    public MatchResult getMatchResult() {
        return matchResult;
    }

    @Override
    public int compareTo(@NonNull IdentificationMatch otherIdentification) {
       return matchResult.compareTo(otherIdentification.matchResult);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof IdentificationMatch) {
            IdentificationMatch other = (IdentificationMatch)o;
            return (guid.equals(other.guid) &&
                    matchResult == other.matchResult);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = guid.hashCode();
        hash = hash * 17 + matchResult.getConfidence();
        hash = hash * 17 + matchResult.getStrength().hashCode();
        return hash;
    }
}

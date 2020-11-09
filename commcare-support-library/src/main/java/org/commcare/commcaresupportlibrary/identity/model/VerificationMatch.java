package org.commcare.commcaresupportlibrary.identity.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Object representing result of a identity verification workflow
 */
@SuppressWarnings({"unused"})
public class VerificationMatch implements Parcelable {

    private String guid;
    private MatchResult matchResult;


    /**
     * Empty Constructor
     */
    public VerificationMatch() {

    }

    /**
     * This constructor creates a new verification
     *
     * @param guid        Identity Provider's Global unique id of the verified person
     * @param matchResult Object representing match attributes like matching confidence and strength
     */
    public VerificationMatch(@NonNull String guid, @NonNull MatchResult matchResult) {
        this.guid = guid;
        this.matchResult = matchResult;
    }

    protected VerificationMatch(Parcel in) {
        guid = in.readString();
        matchResult = in.readParcelable(MatchResult.class.getClassLoader());
    }

    public static final Creator<VerificationMatch> CREATOR = new Creator<VerificationMatch>() {
        @Override
        public VerificationMatch createFromParcel(Parcel in) {
            return new VerificationMatch(in);
        }

        @Override
        public VerificationMatch[] newArray(int size) {
            return new VerificationMatch[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(guid);
        dest.writeParcelable(matchResult, flags);
    }

    public MatchResult getMatchResult() {
        return matchResult;
    }

    @NonNull
    public String getGuid() {
        return guid;
    }
}

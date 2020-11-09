package org.commcare.commcaresupportlibrary.identity.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

public class MatchResult implements Parcelable, Comparable<MatchResult> {

    private int confidence;
    private MatchStrength strength;

    /**
     * @param confidence An int containing the (matching) confidence
     * @param strength   Interpretation of the confidence in terms of MatchStrength
     */
    public MatchResult(int confidence, MatchStrength strength) {
        this.confidence = confidence;
        this.strength = strength;
    }


    protected MatchResult(Parcel in) {
        confidence = in.readInt();
        strength = MatchStrength.values()[in.readInt()];
    }

    public static final Creator<MatchResult> CREATOR = new Creator<MatchResult>() {
        @Override
        public MatchResult createFromParcel(Parcel in) {
            return new MatchResult(in);
        }

        @Override
        public MatchResult[] newArray(int size) {
            return new MatchResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(confidence);
        dest.writeInt(strength.ordinal());
    }

    public int getConfidence() {
        return confidence;
    }

    public MatchStrength getStrength() {
        return strength;
    }

    @Override
    public int compareTo(@NonNull MatchResult o) {
        return Integer.compare(o.getConfidence(), getConfidence());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof MatchResult) {
            MatchResult other = (MatchResult)o;
            return (confidence == other.getConfidence() && strength == other.getStrength());
        }
        return false;
    }
}

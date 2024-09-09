package org.commcare.models.connect;

import android.os.Parcel;
import android.os.Parcelable;

public class ConnectCombineJobListModel implements Parcelable {
    ConnectLoginJobListModel combineAppsList;
    int listType;

    public ConnectCombineJobListModel() {

    }

    public ConnectCombineJobListModel(ConnectLoginJobListModel combineAppsList, int listType) {
        this.combineAppsList = combineAppsList;
        this.listType = listType;
    }

    protected ConnectCombineJobListModel(Parcel in) {
        combineAppsList = in.readParcelable(ConnectLoginJobListModel.class.getClassLoader());
        listType = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(combineAppsList, flags);
        dest.writeInt(listType);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ConnectCombineJobListModel> CREATOR = new Creator<ConnectCombineJobListModel>() {
        @Override
        public ConnectCombineJobListModel createFromParcel(Parcel in) {
            return new ConnectCombineJobListModel(in);
        }

        @Override
        public ConnectCombineJobListModel[] newArray(int size) {
            return new ConnectCombineJobListModel[size];
        }
    };

    public ConnectLoginJobListModel getConnectLoginJobListModel() {
        return combineAppsList;
    }

    public void setConnectLoginJobListModel(ConnectLoginJobListModel combineAppsList) {
        this.combineAppsList = combineAppsList;
    }

    public int getListType() {
        return listType;
    }

    public void setListType(int listType) {
        this.listType = listType;
    }
}

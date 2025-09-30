package com.example.testmap.model;

import android.os.Parcel;
import android.os.Parcelable;

public class StationLite implements Parcelable {
    public String stationId;
    public String stationNm;
    public String arsId;

    public StationLite(String stationId, String stationNm, String arsId) {
        this.stationId = stationId;
        this.stationNm = stationNm;
        this.arsId = arsId;
    }

    protected StationLite(Parcel in) {
        stationId = in.readString();
        stationNm = in.readString();
        arsId = in.readString();
    }

    public static final Creator<StationLite> CREATOR = new Creator<StationLite>() {
        @Override public StationLite createFromParcel(Parcel in) { return new StationLite(in); }
        @Override public StationLite[] newArray(int size) { return new StationLite[size]; }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(stationId);
        dest.writeString(stationNm);
        dest.writeString(arsId);
    }
}

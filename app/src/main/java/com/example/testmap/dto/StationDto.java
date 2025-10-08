package com.example.testmap.dto;

import com.google.gson.annotations.SerializedName;

public class StationDto {
    @SerializedName("arsId")
    public String arsId;
    @SerializedName("stId")
    public long stationId;
    @SerializedName("name")
    public String stationName;
    @SerializedName("lon")
    public double x; // 경도(gpsX)
    @SerializedName("lat")
    public double y; // 위도(gpsY)
}

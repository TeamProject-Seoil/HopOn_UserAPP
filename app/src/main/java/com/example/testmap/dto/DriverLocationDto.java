// app/src/main/java/com/example/testmap/dto/DriverLocationDto.java
package com.example.testmap.dto;

import com.google.gson.annotations.SerializedName;

public class DriverLocationDto {
    @SerializedName(value = "operationId", alternate = {"opId"})
    public Long operationId;

    @SerializedName(value = "driverName", alternate = {"name"})
    public String driverName;

    @SerializedName(value = "driverId",   alternate = {"userid", "userId"})
    public String driverId;

    @SerializedName(value = "company")
    public String company;

    // 번호판: plainNo 또는 plateNo 로 내려와도 받도록
    @SerializedName(value = "plainNo",    alternate = {"plateNo", "plate"})
    public String plainNo;

    // 차량ID: vehId 또는 vehicleId 로 내려와도 받도록
    @SerializedName(value = "vehId",      alternate = {"vehicleId", "busId"})
    public String vehId;

    // 좌표: lat/lng 또는 latitude/longitude 로 내려와도 받도록
    @SerializedName(value = "lat",        alternate = {"latitude", "y"})
    public Double lat;

    @SerializedName(value = "lng",        alternate = {"longitude", "x"})
    public Double lng;

    @SerializedName(value = "updatedAt",  alternate = {"updated_at", "ts", "time"})
    public String updatedAt;
}

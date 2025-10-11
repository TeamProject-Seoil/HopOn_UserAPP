package com.example.testmap.dto;

public class ReservationResponse {
    public Long id;
    public String routeId;
    public String direction;
    public String boardStopId;
    public String boardStopName;
    public String boardArsId;
    public String destStopId;
    public String destStopName;
    public String destArsId;
    public String status;      // "CONFIRMED" 등
    public String updatedAt;   // 필요시

    public String routeName;
}

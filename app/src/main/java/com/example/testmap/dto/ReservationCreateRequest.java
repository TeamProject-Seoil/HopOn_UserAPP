package com.example.testmap.dto;

public class ReservationCreateRequest {
    public String routeId;
    public String direction;
    public String boardStopId;
    public String boardStopName;
    public String boardArsId;
    public String destStopId;
    public String destStopName;
    public String destArsId;
    public String routeName;

    // ▼ 추가 (필요 시)
    public Integer busRouteType;   // 1~9,0 등
    public String  routeTypeName;  // "간선" 등

}

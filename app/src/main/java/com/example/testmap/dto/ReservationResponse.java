package com.example.testmap.dto;

public class ReservationResponse {
    public Long id;
    public String status;  // CONFIRMED, CANCELLED 등
    public String routeId;
    public String direction;
    public String boardStopName;
    public String destStopName;
}

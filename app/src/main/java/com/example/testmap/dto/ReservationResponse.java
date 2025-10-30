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

    // ▼ 매칭/연결 정보 (서버가 주면 받음 — 없으면 null)
    public String apiVehId;      // 공공API vehId
    public String apiPlainNo;    // 공공API plainNo(번호판)
    public Long operationId;     // HopOn 운행 ID (연결된 경우)
}

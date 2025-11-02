// app/src/main/java/com/example/testmap/dto/ReservationResponse.java
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

    public String status;       // "CONFIRMED", "CANCELLED", "COMPLETED" ...
    public String routeName;

    // 서버 LocalDateTime -> ISO 문자열로 내려온다고 가정
    public String updatedAt;

    // ▼ 매칭/연결 정보 (없으면 null)
    public String apiVehId;     // 공공API vehId
    public String apiPlainNo;   // 공공API plainNo(번호판)
    public Long operationId;    // HopOn 운행 ID

    // ▼ 노선유형 (서버가 계산해줌; 없으면 null)
    //   1=공항,2=마을,3=간선,4=지선,5=순환,6=광역,7=인천,8=경기,9=폐지,0=공용
    public Integer busRouteType;
    public String  routeTypeName;

    // ▼ 추가: 지연 상태 (기사 앱에서 delay 버튼 누르면 true)
    public Boolean delayed;        // null 이면 false 취급해도 됨

    // ▼ 추가: 탑승 단계 (서버 BoardingStage 컬럼 값)
    //   예: "NOSHOW" / "BOARDED" / "ALIGHTED"
    public String boardingStage;
}

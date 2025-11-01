// app/src/main/java/com/example/testmap/model/RecentItem.java
package com.example.testmap.model;

import com.example.testmap.dto.ReservationResponse;

public class RecentItem {
    private final String routeId;
    private final String routeName;
    private final String direction;
    private final String boardStopId;
    private final String boardStopName;
    private final String boardArsId;
    private final String destStopId;
    private final String destStopName;
    private final String destArsId;

    // 노선유형(서버 제공)
    private final Integer busRouteType;   // 1=공항,2=마을,3=간선,4=지선,5=순환,6=광역,7=인천,8=경기,9=폐지,0=공용 (nullable)
    private final String  routeTypeName;  // "간선", "지선" 등 (nullable → ""로 보정)

    public RecentItem(ReservationResponse r) {
        this.routeId       = nz(r.routeId);
        this.routeName     = nz(r.routeName);
        this.direction     = nz(r.direction);
        this.boardStopId   = nz(r.boardStopId);
        this.boardStopName = nz(r.boardStopName);
        this.boardArsId    = nz(r.boardArsId);
        this.destStopId    = nz(r.destStopId);
        this.destStopName  = nz(r.destStopName);
        this.destArsId     = nz(r.destArsId);

        this.busRouteType  = r.busRouteType;                 // 그대로 보관(null 허용)
        this.routeTypeName = nz(r.routeTypeName);            // null → ""
    }

    private static String nz(String s){ return s == null ? "" : s; }

    // 화면 표시용
    public String getBusNumber()    { return routeName.isEmpty() ? routeId : routeName; }
    public String getStartStation() { return boardStopName; }
    public String getEndStation()   { return destStopName; }

    // 즐겨찾기/요청용 getter들
    public String  getRouteId()       { return routeId; }
    public String  getRouteName()     { return routeName; }
    public String  getDirection()     { return direction; }
    public String  getBoardStopId()   { return boardStopId; }
    public String  getBoardStopName() { return boardStopName; }
    public String  getBoardArsId()    { return boardArsId; }
    public String  getDestStopId()    { return destStopId; }
    public String  getDestStopName()  { return destStopName; }
    public String  getDestArsId()     { return destArsId; }

    // 노선유형 getter
    public Integer getBusRouteType()   { return busRouteType; }
    public String  getRouteTypeName()  { return routeTypeName; }
}

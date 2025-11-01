// app/src/main/java/com/example/testmap/model/FavoriteItem.java
package com.example.testmap.model;

import androidx.annotation.Nullable;
import java.util.Objects;

public class FavoriteItem {
    private final String busNumber;     // 예: "320"
    private final String busName;       // 예: "울마삼 → 사가정"
    private final String busInfo;       // 예: "평일 운행 / 배차간격 10분"

    // ⬇ 추가: 서버가 내려주는 노선유형
    @Nullable private final Integer busRouteType;  // 1,2,3...
    @Nullable private final String  routeTypeName; // "간선", "지선"...

    public FavoriteItem(String busNumber, String busName, String busInfo) {
        this(busNumber, busName, busInfo, null, null);
    }

    public FavoriteItem(String busNumber, String busName, String busInfo,
                        @Nullable Integer busRouteType, @Nullable String routeTypeName) {
        this.busNumber = busNumber;
        this.busName = busName;
        this.busInfo = busInfo;
        this.busRouteType = busRouteType;
        this.routeTypeName = routeTypeName;
    }

    public String getBusNumber() { return busNumber; }
    public String getBusName() { return busName; }
    public String getBusInfo() { return busInfo; }
    @Nullable public Integer getBusRouteType() { return busRouteType; }
    @Nullable public String getRouteTypeName() { return routeTypeName; }

    /** 서버 응답 매핑 헬퍼 */
    public static FavoriteItem fromApi(String routeName, String titleLine,
                                       String infoLine,
                                       @Nullable Integer busRouteType,
                                       @Nullable String routeTypeName) {
        // routeName = 버스번호(예: "320")
        // titleLine = 상단 제목(예: "울마삼 → 사가정") - 필요에 맞게 바꿔서 넘겨
        // infoLine  = 하단 서브(예: "평일 운행 / 배차간격 10분")
        return new FavoriteItem(routeName, titleLine, infoLine, busRouteType, routeTypeName);
    }

    // 선택: DiffUtil 위해 동등성 정의
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FavoriteItem)) return false;
        FavoriteItem that = (FavoriteItem) o;
        return Objects.equals(busNumber, that.busNumber) &&
                Objects.equals(busName, that.busName) &&
                Objects.equals(busInfo, that.busInfo) &&
                Objects.equals(busRouteType, that.busRouteType) &&
                Objects.equals(routeTypeName, that.routeTypeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(busNumber, busName, busInfo, busRouteType, routeTypeName);
    }
}

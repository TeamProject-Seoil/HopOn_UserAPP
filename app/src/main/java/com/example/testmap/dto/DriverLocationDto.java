// app/src/main/java/com/example/testmap/dto/DriverLocationDto.java
package com.example.testmap.dto;

import androidx.annotation.Nullable;

import com.naver.maps.geometry.LatLng;

/**
 * 예약과 매칭된 버스(또는 HopOn 운행)의 현재 위치 DTO
 * 서버 응답 예:
 * {
 *   "operationId": 123,             // null 가능 (HopOn 운행과 아직 미연결)
 *   "lat": 37.5665,
 *   "lon": 126.9780,
 *   "updatedAtIso": "2025-10-30T12:34:56",
 *   "stale": false
 * }
 */
public class DriverLocationDto {
    /** HopOn 운행 ID (없을 수 있음) */
    @Nullable
    public Long operationId;

    /** 위도 (null 가능) */
    @Nullable
    public Double lat;

    /** 경도 (null 가능) */
    @Nullable
    public Double lon;

    /** 서버 기준 갱신 시각(ISO 문자열, 예: 2025-10-30T12:34:56) */
    @Nullable
    public String updatedAtIso;

    /** 데이터가 오래되었는지(표시만) */
    public boolean stale;

    // ---- 편의 메서드 ----

    /** 좌표가 유효한지 간단 체크 */
    public boolean hasValidCoord() {
        return lat != null && lon != null
                && lat >= -90 && lat <= 90
                && lon >= -180 && lon <= 180;
    }

    /** 네이버맵 LatLng로 변환 (좌표 없으면 null) */
    @Nullable
    public LatLng toLatLng() {
        return hasValidCoord() ? new LatLng(lat, lon) : null;
    }

    /** HopOn 운행과 연결되어 있는지 여부 */
    public boolean hasOperation() {
        return operationId != null && operationId > 0;
    }
}

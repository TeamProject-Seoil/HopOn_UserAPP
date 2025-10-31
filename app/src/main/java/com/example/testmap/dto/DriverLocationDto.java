// app/src/main/java/com/example/testmap/dto/DriverLocationDto.java
package com.example.testmap.dto;

public class DriverLocationDto {
    public Double lat;
    public Double lng;        // 서버에서 @JsonProperty("lng") 매핑됨
    public Long operationId;
    public String updatedAtIso;
    public boolean stale;
    public String plainNo;    // ← 이거 추가해야 d.plainNo 접근 가능
    public String routeType;
}

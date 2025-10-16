package com.example.testmap.model;

public class FavoriteItem {
    private String busNumber;  // 버스 번호 (예: "320")
    private String busName;    // 노선 이름 (예: "울마삼 → 사가정")
    private String busInfo;    // 추가 정보 (예: "평일 운행 / 배차간격 10분")

    public FavoriteItem(String busNumber, String busName, String busInfo) {
        this.busNumber = busNumber;
        this.busName = busName;
        this.busInfo = busInfo;
    }

    public String getBusNumber() {
        return busNumber;
    }

    public String getBusName() {
        return busName;
    }

    public String getBusInfo() {
        return busInfo;
    }
}

package com.example.testmap.model;

public class RecentItem {
    private String busNumber;
    private String startStation;
    private String endStation;

    public RecentItem(String busNumber, String startStation, String endStation) {
        this.busNumber = busNumber;
        this.startStation = startStation;
        this.endStation = endStation;
    }

    public String getBusNumber() { return busNumber; }
    public String getStartStation() { return startStation; }
    public String getEndStation() { return endStation; }
}

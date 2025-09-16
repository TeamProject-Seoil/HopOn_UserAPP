package com.example.testmap.dto;

public class BusLocationDto {
    public String vehId; //차량Id
    public String plainNo; //번호판
    public String busType; //0:일반, 1:저상
    public String nextStId; //다음정류장
    public String congetion; //혼잡도(0:정보없음, 3:여유, 4:보통, 5:혼잡, 6:매우혼잡)
    public String gpsX; //x좌표
    public String gpsY; //y좌표

    public Integer sectOrd; //구간 순번
    public String stopFlag; //정류소 도착 여부(1이면 도착 0이면 운행중)
}

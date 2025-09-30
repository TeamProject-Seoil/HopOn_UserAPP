package com.example.testmap.activity;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.testmap.R;
import com.example.testmap.adapter.BusRouteAdapter;
import com.example.testmap.dto.BusLocationDto;
import com.example.testmap.dto.BusRouteDto;
import com.example.testmap.dto.StationDto;
import com.example.testmap.service.ApiClient;
import com.example.testmap.ui.BusOverlayDecoration;
import com.example.testmap.ui.ReserveDialogFragment;
import com.example.testmap.util.BusColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import retrofit2.*;

import android.content.Intent;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;

public class BusRouteActivity extends AppCompatActivity {

    public static final String EXTRA_ROUTE_NAME = "extra_route_name";  //버스번호 상수 (아마도?)
    public static final String EXTRA_BUS_ROUTE_ID = "extra_bus_route_id";   //버스루트아이디 상수
    public static final String EXTRA_BUS_ROUTETYPE = "extra_bus_routetype"; //간선 지선 종류 상수

    //출발 정류장 지정
    public static final String EXTRA_DEPARTURE = "extra_departure_station";

    //정류장 클릭시 포커스
    public static final String EXTRA_FOCUS_STATION_ID = "extra_focus_station_id";
    public static final String EXTRA_FOCUS_SEQ       = "extra_focus_seq";
    public static final String EXTRA_FOCUS_ARS_ID = "extra_focus_ars_id";

    //출발정류장 지정
    public static final String EXTRA_DEPARTURE_NAME = "extra_departure_name";
    public static final String EXTRA_DEPARTURE_ARS  = "extra_departure_ars";
    private String departureName; // 고정 출발역 이름
    private String departureArs;  // 고정 출발역 arsId



    private BusRouteAdapter adapter;
    RecyclerView rv;

    // 상행/하행
    private List<BusRouteDto> allStops = new ArrayList<>();
    private String dirLeft = null, dirRight = null;
    private String currentDir = null;
    private TextView tvLeft, tvRight;

    // 경로 표시
    private String currentBusRouteId;
    private final Map<String, Boolean> visibleSeq = new HashMap<>();
    private final List<BusRouteDto> currentDirectionStops = new ArrayList<>();

    // 정류장ID -> seq들(중복 방지용)
    private final Map<String, List<Integer>> seqsByStId = new HashMap<>();

    // 차량별 직전 nextSeq (역주행/점프 완화)
    private final Map<String, Integer> lastNextSeqByVeh = new HashMap<>();

    // 현재 방면의 마지막 정류장 정보 (회차 예외 허용)
    private int endSeq = 1;
    private String endStationId = null;

    //노선 표시
    private int dirSeqMin = 1, dirSeqMax = 1;
    private final Map<String,Integer> lastSeqByStation = new HashMap<>();
    private final Map<String,Integer> nextSeqBySection = new HashMap<>();

    //정류장 클릭시 포커스
    private String  focusStationId;
    private Integer focusSeq;
    private String focusArsId;

    //버스 아이콘 위치 동기화 시간 설정
    private final android.os.Handler handler = new android.os.Handler();
    private final int REFRESH_INTERVAL = 10000; // 10초

    private com.example.testmap.model.StationLite departureStation;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (currentBusRouteId != null) {
                loadAndApplyBusLocations(currentBusRouteId);
            }
            // 주기적으로 다시 실행
            handler.postDelayed(this, REFRESH_INTERVAL);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(refreshTask, REFRESH_INTERVAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTask); // 화면 나가면 중지
    }

    private void indexVisibleBySeq(List<BusRouteDto> visible) {
        visibleSeq.clear();
        for (BusRouteDto s : visible) visibleSeq.put(s.seq, true);
    }

    // ▼▼▼ [추가] 방면 탭 색상 전환 유틸 ▼▼▼
    private void selectLeft() {
        if (tvLeft == null || tvRight == null) return;
        tvLeft.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        tvLeft.setTextColor(ContextCompat.getColor(this, android.R.color.black));

        tvRight.setBackgroundColor(ContextCompat.getColor(this, R.color.mainblue));
        tvRight.setTextColor(ContextCompat.getColor(this, android.R.color.white));
    }

    private void selectRight() {
        if (tvLeft == null || tvRight == null) return;
        tvRight.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        tvRight.setTextColor(ContextCompat.getColor(this, android.R.color.black));

        tvLeft.setBackgroundColor(ContextCompat.getColor(this, R.color.mainblue));
        tvLeft.setTextColor(ContextCompat.getColor(this, android.R.color.white));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bus_route);

        String routeNm = getIntent().getStringExtra(EXTRA_ROUTE_NAME);
        String busRouteId = getIntent().getStringExtra(EXTRA_BUS_ROUTE_ID);
        String routeType = getIntent().getStringExtra(EXTRA_BUS_ROUTETYPE);
        String defaultDir = getIntent().getStringExtra("extra_selected_dir");
        currentBusRouteId = getIntent().getStringExtra(EXTRA_BUS_ROUTE_ID);

        TextView title = findViewById(R.id.bus_number);
        if (title != null && routeNm != null) title.setText(routeNm);

        rv = findViewById(R.id.bus_route_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BusRouteAdapter();
        rv.setAdapter(adapter);
        rv.addItemDecoration(new BusOverlayDecoration(this, rv, adapter));
        rv.setLayerType(View.LAYER_TYPE_SOFTWARE, null); // 그림자 표시용

        // 상단 아이콘 색
        ImageView busIcon = findViewById(R.id.bus_icon);
        int color = ContextCompat.getColor(this, BusColors.forRouteType(routeType));
        busIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        // 뒤로가기
        ImageView backIcon = findViewById(R.id.back_icon);
        backIcon.setOnClickListener(v -> finish());

        // 방면 탭
        tvLeft = findViewById(R.id.direction_left);
        tvRight = findViewById(R.id.direction_right);

        //왼쪽탭 선택
        selectLeft();

        tvLeft.setOnClickListener(v -> switchDirection(dirLeft));
        tvRight.setOnClickListener(v -> switchDirection(dirRight));

        //정류장 화면 포커스
        focusStationId = getIntent().getStringExtra(EXTRA_FOCUS_STATION_ID);
        focusArsId = getIntent().getStringExtra(EXTRA_FOCUS_ARS_ID);
        int tmpSeq = getIntent().getIntExtra(EXTRA_FOCUS_SEQ, -1);
        focusSeq = (tmpSeq > 0) ? tmpSeq : null;

        // ★ 한 번만 호출
        fetchStops(busRouteId, defaultDir);

        //예약화면 연결 코드

        //출발 정류장 지정
        departureName = getIntent().getStringExtra(EXTRA_DEPARTURE_NAME);
        departureArs  = getIntent().getStringExtra(EXTRA_DEPARTURE_ARS);
        if (!TextUtils.isEmpty(departureName)) {
            Toast.makeText(this, "출발역: " + departureName, Toast.LENGTH_SHORT).show();
        }

        // 정류장 클릭 이벤트 연결
        adapter.setOnStopClickListener(stop -> {
            String arrivalName = stop.stationNm;              // 도착역(클릭한 정류장)
            String routeName   = routeNm;                     // 현재 버스 번호
            String depName     = !TextUtils.isEmpty(departureName) ? departureName : "출발역 미지정";

            // 예약 다이얼로그(출발=고정, 도착=클릭)
            ReserveDialogFragment dialog = ReserveDialogFragment.newInstance(
                    depName,
                    arrivalName,
                    routeName
            );
            dialog.setOnReserveListener((dep, arr, route, boardingAlarm, dropOffAlarm) -> {
                Toast.makeText(this, "예약: " + dep + " → " + arr + " (" + route + ")", Toast.LENGTH_SHORT).show();
                // TODO: 서버 예약 API 호출 시 depName/arrivalName/routeName 이용
            });
            dialog.show(getSupportFragmentManager(), "reserve_dialog");
        });

    }

    private void fetchStops(String busRouteId, String defaultDir) {
        if (busRouteId == null || busRouteId.isEmpty()) {
            Toast.makeText(this, "busRoute 없음", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.get().getBusRoute(busRouteId).enqueue(new Callback<List<BusRouteDto>>() {
            @Override
            public void onResponse(Call<List<BusRouteDto>> c, Response<List<BusRouteDto>> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    Toast.makeText(BusRouteActivity.this, "HTTP " + r.code(), Toast.LENGTH_SHORT).show();
                    return;
                }

                // 1) 전체 저장
                allStops.clear();
                allStops.addAll(r.body());

                // 2) 서로 다른 direction 2개 추출
                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (BusRouteDto s : allStops) set.add(s.direction);
                List<String> dirs = new ArrayList<>(set);
                dirLeft = dirs.size() >= 1 ? dirs.get(0) : "방면 A";
                dirRight = dirs.size() >= 2 ? dirs.get(1) : dirLeft;

                // 3) 탭 라벨
                tvLeft.setText(dirLeft + " 방면");
                tvRight.setText(dirRight + " 방면");

                // 4) 기본 방면 선택 (인텐트로 온 값 우선)
                String first = pickInitialDirection(defaultDir, focusStationId, focusSeq);
                switchDirection(first);
            }

            @Override
            public void onFailure(Call<List<BusRouteDto>> c, Throwable t) {
                Toast.makeText(BusRouteActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String pickInitialDirection(@Nullable String defaultDir,
                                        @Nullable String focusStationId,
                                        @Nullable Integer focusSeq) {
        // 2-A) focusArsId가 특정 방면에만 있으면 그 방면 선택  ★ 추가
        if (focusArsId != null) {
            boolean inLeft = false, inRight = false;
            for (BusRouteDto s : allStops) {
                if (focusArsId.equals(s.arsId)) {
                    if (s.direction != null && s.direction.equals(dirLeft))  inLeft = true;
                    if (s.direction != null && s.direction.equals(dirRight)) inRight = true;
                }
            }
            if (inLeft ^ inRight) return inLeft ? dirLeft : dirRight;
        }

        // 2-B) 기존 로직: stationId 기준
        if (focusStationId != null) {
            boolean inLeft = false, inRight = false;
            for (BusRouteDto s : allStops) {
                if (focusStationId.equals(s.station)) {
                    if (s.direction != null && s.direction.equals(dirLeft))  inLeft  = true;
                    if (s.direction != null && s.direction.equals(dirRight)) inRight = true;
                }
            }
            if (inLeft ^ inRight) return inLeft ? dirLeft : dirRight;
        }

        // 2-C) 기존 로직: seq 기준
        if (focusSeq != null) {
            for (BusRouteDto s : allStops) {
                if (safeInt(s.seq, -1) == focusSeq) return s.direction;
            }
        }

        // 2-D) 기본값
        if (defaultDir != null && (defaultDir.equals(dirLeft) || defaultDir.equals(dirRight))) {
            return defaultDir;
        }
        return dirLeft;
    }

    private void switchDirection(String dir) {
        if (dir == null) return;
        currentDir = dir;

        // ▼▼▼ [추가] 현재 선택된 방면에 맞춰 탭 색상 동기화 ▼▼▼
        if (dir.equals(dirLeft)) selectLeft();
        else selectRight();

        List<BusRouteDto> filtered = new ArrayList<>();
        for (BusRouteDto s : allStops) if (dir.equals(s.direction)) filtered.add(s);

        adapter.submit(filtered);

        // ✅ 출발역 강조 고정: 출발 arsId를 어댑터에 알려주기
        adapter.setDepartureArsId(departureArs);

        indexVisibleBySeq(filtered);
        currentDirectionStops.clear();
        currentDirectionStops.addAll(filtered);

        buildIndexForDirection(filtered);
        lastNextSeqByVeh.clear();
        loadAndApplyBusLocations(currentBusRouteId);

        centerOnFocusIfAny(filtered);
    }

    //정류장을 중앙으로
    private void centerOnFocusIfAny(List<BusRouteDto> list) {
        int pos = findFocusPosition(list);
        if (pos >= 0) {
            smoothCenterTo(pos);
            //adapter.setSelectedPos(pos); // 선택 상태 반영 → 내위치 아이콘 뜸
        }
    }
    private int findFocusPosition(List<BusRouteDto> list) {
        if (focusSeq != null) {
            for (int i = 0; i < list.size(); i++) {
                if (safeInt(list.get(i).seq, -1) == focusSeq) return i;
            }
        }
        if (focusStationId != null) {
            for (int i = 0; i < list.size(); i++) {
                if (focusStationId.equals(list.get(i).station)) return i;
            }
        }
        if (focusArsId != null) {                // ← arsId도 지원
            for (int i = 0; i < list.size(); i++) {
                if (focusArsId.equals(list.get(i).arsId)) return i;
            }
        }
        return -1;
    }

    //스크롤 함수
    private void smoothCenterTo(int position) {
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) {
            rv.scrollToPosition(position);
            return;
        }
        LinearLayoutManager llm = (LinearLayoutManager) lm;

        // 1차: 대략 가운데
        rv.post(() -> {
            llm.scrollToPositionWithOffset(position, rv.getHeight() / 2);

            // 2차: 뷰가 붙은 뒤 정확히 중앙으로 미세 조정
            rv.post(() -> {
                View v = llm.findViewByPosition(position);
                if (v != null) {
                    int offset = (rv.getHeight() - v.getHeight()) / 2;
                    if (offset < 0) offset = 0;
                    llm.scrollToPositionWithOffset(position, offset);
                }
            });
        });
    }
    // 위치 불러와서 “다음정류장 seq → t” 생성
    private void loadAndApplyBusLocations(String busRouteId) {
        ApiClient.get().getBusLocation(busRouteId).enqueue(new Callback<List<BusLocationDto>>() {
            @Override public void onResponse(Call<List<BusLocationDto>> c, Response<List<BusLocationDto>> r) {
                if (!r.isSuccessful() || r.body() == null) return;

                List<BusRouteAdapter.BusMark> marks = new ArrayList<>();
                Map<Integer,Integer> dup = new HashMap<>();

                for (BusLocationDto b : r.body()) {
                    Integer nextSeq = null;

                    // 1) sectionId → nextSeq
                    if (b.sectionId != null) {
                        nextSeq = nextSeqBySection.get(b.sectionId.trim());
                    }

                    // 2) 없으면 lastStnId → lastSeq(+1)
                    if (nextSeq == null && b.lastStnId != null) {
                        Integer lastSeq = lastSeqByStation.get(b.lastStnId.trim());
                        if (lastSeq != null) nextSeq = Math.min(lastSeq + 1, dirSeqMax);
                    }

                    if (nextSeq == null) continue; // 현재 방면에 못 매핑
                    if (nextSeq < dirSeqMin || nextSeq > dirSeqMax) continue; // 반대 방면

                    // 3) 표시 seq: 정차면 lastSeq, 운행이면 nextSeq
                    boolean stopped = "1".equals(b.stopFlag);
                    int displaySeq;
                    if (stopped && b.lastStnId != null) {
                        Integer lastSeq = lastSeqByStation.get(b.lastStnId.trim());
                        displaySeq = (lastSeq != null) ? lastSeq : Math.max(dirSeqMin, nextSeq - 1);
                    } else {
                        displaySeq = nextSeq;
                    }
                    if (displaySeq < dirSeqMin || displaySeq > dirSeqMax) continue;

                    // 4) 진행률 t
                    float t;
                    if (stopped) {
                        t = 1f;
                    } else if (b.fullSectDist != null && b.sectDist != null && b.fullSectDist > 0) {
                        t = (float)Math.max(0, Math.min(1, b.sectDist / b.fullSectDist));
                    } else {
                        t = 0.5f;
                    }

                    // 5) 팬아웃
                    int n = dup.getOrDefault(displaySeq, 0);
                    dup.put(displaySeq, n + 1);
                    if (!stopped) t = Math.max(0f, Math.min(1f, t + 0.08f * n));

                    // 6) 라벨
                    String number = tailDigits(b.plainNo, 4);
                    if (number.isEmpty()) number = tailDigits(b.vehId, 4);
                    String cong = toKoreanCong(b.congetion);
                    boolean low = "1".equals(b.busType);

                    marks.add(new BusRouteAdapter.BusMark(displaySeq, t, number, cong, low));
                }

                runOnUiThread(() -> {
                    adapter.setBusMarks(marks);
                    rv.invalidateItemDecorations();
                    rv.invalidate();
                    rv.postInvalidateOnAnimation();
                });
            }

            @Override
            public void onFailure(Call<List<BusLocationDto>> c, Throwable t) { }
        });
    }

    private BusRouteDto findStopBySeq(int seq) {
        for (BusRouteDto s : currentDirectionStops) { // ★ 현재 방면에서만 탐색
            try { if (Integer.parseInt(s.seq) == seq) return s; } catch (Exception ignore) {}
        }
        return null;
    }
    private double dist(BusRouteDto stop, double x, double y) {
        double dx = (stop.gpsX != null ? Double.parseDouble(stop.gpsX) : 0) - x;
        double dy = (stop.gpsY != null ? Double.parseDouble(stop.gpsY) : 0) - y;
        return Math.hypot(dx, dy);
    }


    private String tailDigits(String s, int n) {
        if (s == null) return "";
        String d = s.replaceAll("\\D+", "");
        if (d.length() < n) return d;
        return d.substring(d.length()-n);
    }

    private String toKoreanCong(String v) {
        if (v == null) return "";
        switch (v.trim()) {
            case "3": return "여유";
            case "4": return "보통";
            case "5": return "혼잡";
            case "6": return "매우혼잡";
            default:  return "";
        }
    }

    private String makeLabel(BusLocationDto b) {
        // 번호: plainNo 끝 4자리 or vehId 끝 4
        String num = "";
        if (b.plainNo != null) {
            String onlyDigits = b.plainNo.replaceAll("\\D+", "");
            if (onlyDigits.length() >= 4) num = onlyDigits.substring(onlyDigits.length()-4);
        }
        if (num.isEmpty() && b.vehId != null && b.vehId.length() >= 4) {
            num = b.vehId.substring(b.vehId.length()-4);
        }
        // 혼잡도
        String cong;
        String v = b.congetion == null ? "" : b.congetion.trim();
        switch (v) {
            case "3": cong = "여유"; break;
            case "4": cong = "보통"; break;
            case "5": cong = "혼잡"; break;
            case "6": cong = "매우혼잡"; break;
            default : cong = "";     break;
        }
        // 저상
        String low = "1".equals(b.busType) ? "저상" : "";

        // 조합
        StringBuilder sb = new StringBuilder();
        if (!num.isEmpty()) sb.append(num);
        if (!cong.isEmpty()) { if (sb.length()>0) sb.append(' '); sb.append(cong); }
        if (!low.isEmpty())  { if (sb.length()>0) sb.append(' '); sb.append(low); }
        return sb.toString();
    }

    private int safeInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private void buildIndexForDirection(List<BusRouteDto> stops) {
        lastSeqByStation.clear();
        nextSeqBySection.clear();

        int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
        for (BusRouteDto s : stops) {
            int seq = safeInt(s.seq, -1);
            if (seq < 1) continue;
            mn = Math.min(mn, seq);
            mx = Math.max(mx, seq);

            if (s.station != null) {
                lastSeqByStation.put(s.station, seq); // 같은 정류장은 마지막 seq 유지
            }
            if (s.section != null) {
                String sec = s.section.trim();
                if (!sec.isEmpty()) nextSeqBySection.put(sec, seq); // section -> nextSeq
            }
        }
        if (mn == Integer.MAX_VALUE) { mn = 1; mx = 1; }
        dirSeqMin = mn;
        dirSeqMax = mx;

        // 시작 구간 보정
        nextSeqBySection.putIfAbsent("0", dirSeqMin);

        // 회차 관련 표시용(옵션)
        endSeq = dirSeqMax;
        endStationId = stops.isEmpty() ? null : stops.get(stops.size()-1).station;
    }
}



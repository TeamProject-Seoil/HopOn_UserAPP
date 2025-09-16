package com.example.testmap.activity;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.testmap.R;
import com.example.testmap.adapter.BusRouteAdapter;
import com.example.testmap.dto.BusLocationDto;
import com.example.testmap.dto.BusRouteDto;
import com.example.testmap.service.ApiClient;
import com.example.testmap.ui.BusOverlayDecoration;
import com.example.testmap.util.BusColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import retrofit2.*;

public class
BusRouteActivity extends AppCompatActivity {

    public static final String EXTRA_ROUTE_NAME = "extra_route_name";  //버스번호 상수 (아마도?)

    public static final String EXTRA_BUS_ROUTE_ID = "extra_bus_route_id";   //버스루트아이디 상수

    public static final String EXTRA_BUS_ROUTETYPE = "extra_bus_routetype"; //간선 지선 종류 상수

    private BusRouteAdapter adapter;

    RecyclerView rv;

    //버스 상행,하행을 나타내기위한 변수선언부분
    private List<BusRouteDto> allStops = new ArrayList<>();
    private String dirLeft = null, dirRight = null;
    private String currentDir = null;
    private TextView tvLeft, tvRight;

    //버스경로 표시를 휘한 부분
    private String currentBusRouteId;
    private final Map<String, Boolean> visibleSeq = new HashMap<>();

    private void indexVisibleBySeq(List<BusRouteDto> visible) {
        visibleSeq.clear();
        for (BusRouteDto s : visible) visibleSeq.put(s.seq, true);
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

        tvLeft.setOnClickListener(v -> switchDirection(dirLeft));
        tvRight.setOnClickListener(v -> switchDirection(dirRight));

        // ★ 한 번만 호출
        fetchStops(busRouteId, defaultDir);

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
                String first = (defaultDir != null && (defaultDir.equals(dirLeft) || defaultDir.equals(dirRight)))
                        ? defaultDir : dirLeft;
                switchDirection(first);
            }

            @Override
            public void onFailure(Call<List<BusRouteDto>> c, Throwable t) {
                Toast.makeText(BusRouteActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void switchDirection(String dir) {
        if (dir == null) return;
        List<BusRouteDto> filtered = new ArrayList<>();
        for (BusRouteDto s : allStops) {
            if (dir.equals(s.direction)) filtered.add(s);
        }
        adapter.submit(filtered);
        indexVisibleBySeq(filtered);

        // 방면 바꾸면 위치도 다시 반영
        loadAndApplyBusLocations(currentBusRouteId);
    }

    // 위치 불러와서 “다음정류장 seq → t” 맵으로 변환
// BusRouteActivity.java (버스 위치 불러온 뒤)
    private void loadAndApplyBusLocations(String busRouteId) {
        ApiClient.get().getBusLocation(busRouteId).enqueue(new Callback<List<BusLocationDto>>() {
            @Override public void onResponse(Call<List<BusLocationDto>> c, Response<List<BusLocationDto>> r) {
                if (!r.isSuccessful() || r.body() == null) return;

                List<BusRouteAdapter.BusMark> marks = new ArrayList<>();
                Map<Integer,Integer> dup = new HashMap<>();

                for (BusLocationDto b : r.body()) {
                    if (b.sectOrd == null) continue;
                    int nextSeq = b.sectOrd + 1;
                    if (!visibleSeq.containsKey(String.valueOf(nextSeq))) continue;

                    float t = "1".equals(b.stopFlag) ? 1f : 0.5f;
                    int n = dup.getOrDefault(nextSeq, 0);
                    dup.put(nextSeq, n+1);
                    t = Math.max(0f, Math.min(1f, t + 0.08f * n));

                    String number = tailDigits(b.plainNo, 4);
                    if (number.isEmpty()) number = tailDigits(b.vehId, 4);

                    String cong = toKoreanCong(b.congetion);     // 3/4/5/6 → 여유/보통/혼잡/매우혼잡
                    boolean low = "1".equals(b.busType);

                    marks.add(new BusRouteAdapter.BusMark(nextSeq, t, number, cong, low));
                }

                adapter.setBusMarks(marks);
                rv.invalidateItemDecorations();
            }

            @Override
            public void onFailure(Call<List<BusLocationDto>> c, Throwable t) {
            }
        });
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
}

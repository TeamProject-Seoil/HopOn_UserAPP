package com.example.testmap.activity;

import android.app.Dialog;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.adapter.BusRouteAdapter;
import com.example.testmap.dto.BusLocationDto;
import com.example.testmap.dto.BusRouteDto;
import com.example.testmap.dto.ReservationCreateRequest;
import com.example.testmap.dto.ReservationResponse;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService; // 즐겨찾기 DTO
import com.example.testmap.ui.BusOverlayDecoration;
import com.example.testmap.ui.LoginRequiredDialogFragment;
import com.example.testmap.ui.ReserveDialogFragment;
import com.example.testmap.ui.UiDialogs;
import com.example.testmap.util.BusColors;
import com.example.testmap.util.TokenStore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BusRouteActivity extends AppCompatActivity {

    public static final String EXTRA_ROUTE_NAME   = "extra_route_name";
    public static final String EXTRA_BUS_ROUTE_ID = "extra_bus_route_id";
    public static final String EXTRA_BUS_ROUTETYPE= "extra_bus_routetype";

    public static final String EXTRA_DEPARTURE    = "extra_departure_station";

    public static final String EXTRA_FOCUS_STATION_ID = "extra_focus_station_id";
    public static final String EXTRA_FOCUS_SEQ        = "extra_focus_seq";
    public static final String EXTRA_FOCUS_ARS_ID     = "extra_focus_ars_id";

    public static final String EXTRA_DEPARTURE_NAME = "extra_departure_name";
    public static final String EXTRA_DEPARTURE_ARS  = "extra_departure_ars";

    private String departureName;
    private String departureArs;

    private BusRouteAdapter adapter;
    RecyclerView rv;

    private List<BusRouteDto> allStops = new ArrayList<>();
    private String dirLeft = null, dirRight = null;
    private String currentDir = null;
    private TextView tvLeft, tvRight;
    private View underline;
    private LinearLayout directionLayout;

    private String currentBusRouteId;
    private final Map<String, Boolean> visibleSeq = new HashMap<>();
    private final List<BusRouteDto> currentDirectionStops = new ArrayList<>();

    private final Map<String,Integer> lastSeqByStation = new HashMap<>();
    private final Map<String,Integer> nextSeqBySection = new HashMap<>();

    private String  focusStationId;
    private Integer focusSeq;
    private String  focusArsId;

    private final android.os.Handler handler = new android.os.Handler();
    private final int REFRESH_INTERVAL = 10000; // 10초

    private int dirSeqMin = 1, dirSeqMax = 1;

    // 즐겨찾기 캐시
    private final List<ApiService.FavoriteResponse> favList = new ArrayList<>();

    // ===== Window leak 방지용 =====
    private Dialog routeDialog;       // 마지막으로 띄운 다이얼로그 보관

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (currentBusRouteId != null) {
                loadAndApplyBusLocations(currentBusRouteId);
            }
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
        handler.removeCallbacks(refreshTask);
    }

    private void indexVisibleBySeq(List<BusRouteDto> visible) {
        visibleSeq.clear();
        for (BusRouteDto s : visible) visibleSeq.put(s.seq, true);
    }

    private void animateTab(int index) {
        int tabWidth = directionLayout.getWidth() / 2;
        float targetX = tabWidth * index;

        underline.animate()
                .translationX(targetX)
                .setDuration(250)
                .start();
    }

    private void updateTabStyle(int index) {
        tvLeft.setTextColor(index == 0 ?
                ContextCompat.getColor(this, R.color.mainblue) :
                ContextCompat.getColor(this, R.color.grayText));

        tvRight.setTextColor(index == 1 ?
                ContextCompat.getColor(this, R.color.mainblue) :
                ContextCompat.getColor(this, R.color.grayText));

        tvLeft.setTypeface(null, index == 0 ?
                android.graphics.Typeface.BOLD :
                android.graphics.Typeface.NORMAL);

        tvRight.setTypeface(null, index == 1 ?
                android.graphics.Typeface.BOLD :
                android.graphics.Typeface.NORMAL);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bus_route);

        String routeNm    = getIntent().getStringExtra(EXTRA_ROUTE_NAME);
        String busRouteId = getIntent().getStringExtra(EXTRA_BUS_ROUTE_ID);
        String routeType  = getIntent().getStringExtra(EXTRA_BUS_ROUTETYPE);
        String defaultDir = getIntent().getStringExtra("extra_selected_dir");
        currentBusRouteId = getIntent().getStringExtra(EXTRA_BUS_ROUTE_ID);

        TextView title = findViewById(R.id.bus_number);
        if (title != null && routeNm != null) title.setText(routeNm);

        rv = findViewById(R.id.bus_route_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BusRouteAdapter();
        rv.setAdapter(adapter);
        rv.addItemDecoration(new BusOverlayDecoration(this, rv, adapter));

        ImageView busIcon = findViewById(R.id.bus_icon);
        int color = ContextCompat.getColor(this, BusColors.forRouteType(routeType));
        busIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        ImageView backIcon = findViewById(R.id.back_icon);
        backIcon.setOnClickListener(v -> finish());

        tvLeft  = findViewById(R.id.direction_left);
        tvRight = findViewById(R.id.direction_right);
        underline = findViewById(R.id.underline);
        directionLayout = findViewById(R.id.direction_layout);

        directionLayout.post(() -> {
            int tabWidth = directionLayout.getWidth() / 2;
            underline.getLayoutParams().width = tabWidth;
            underline.requestLayout();
        });

        tvLeft.setOnClickListener(v -> {
            animateTab(0);
            updateTabStyle(0);
            switchDirection(dirLeft);
        });
        tvRight.setOnClickListener(v -> {
            animateTab(1);
            updateTabStyle(1);
            switchDirection(dirRight);
        });

        focusStationId = getIntent().getStringExtra(EXTRA_FOCUS_STATION_ID);
        focusArsId     = getIntent().getStringExtra(EXTRA_FOCUS_ARS_ID);
        int tmpSeq     = getIntent().getIntExtra(EXTRA_FOCUS_SEQ, -1);
        focusSeq       = (tmpSeq > 0) ? tmpSeq : null;

        departureName = getIntent().getStringExtra(EXTRA_DEPARTURE_NAME);
        departureArs  = getIntent().getStringExtra(EXTRA_DEPARTURE_ARS);
        if (!TextUtils.isEmpty(departureName)) {
            Toast.makeText(this, "출발역: " + departureName, Toast.LENGTH_SHORT).show();
        }

        // BusColors 유틸로 색상 리소스 ID 얻기
        int timelineColor = ContextCompat.getColor(this, BusColors.forRouteType(routeType));

        // RecyclerView 설정
        rv = findViewById(R.id.bus_route_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BusRouteAdapter();
        rv.setAdapter(adapter);

        // 어댑터에도 색상 반영 (세로 라인 색상)
        adapter.setTimelineColor(timelineColor);

        // BusOverlayDecoration에도 같은 색상 적용
        rv.addItemDecoration(new BusOverlayDecoration(this, rv, adapter));

        // 정류장/방면 데이터
        fetchStops(busRouteId, defaultDir);

        // 즐겨찾기 목록 1회 로드
        fetchFavoritesOnce();

        // 정류장 클릭 리스너
        adapter.setOnStopClickListener(arr -> {
            // 0) 출발역 유효성
            if (TextUtils.isEmpty(departureArs)) {
                Toast.makeText(this, "출발역이 지정되지 않았습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1) 현재 방면에 출발역 존재 여부
            BusRouteDto dep = findByArsInCurrentDir(departureArs);
            if (dep == null) {
                String opp = oppositeDirOf(currentDir);
                if (findByArsInDir(departureArs, opp) != null) {
                    new AlertDialog.Builder(this)
                            .setMessage("출발역이 현재 방면에 없습니다.\n" + opp + " 방면으로 전환할까요?")
                            .setPositiveButton("전환", (d, w) -> {
                                switchDirection(opp);
                                Toast.makeText(this, "방면을 전환했어요. 다시 도착 정류장을 선택하세요.", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("취소", null)
                            .show();
                } else {
                    Toast.makeText(this, "출발역이 이 노선의 유효 정류장이 아닙니다.", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // 2) 진행방향 검증 (arr > dep)
            int depSeq = safeInt(dep.seq, -1);
            int arrSeq = safeInt(arr.seq, -1);
            if (depSeq < 0 || arrSeq < 0) {
                Toast.makeText(this, "정류장 순서 정보가 부족합니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (arrSeq <= depSeq) {
                String opp = oppositeDirOf(currentDir);
                BusRouteDto depInOpp = findByArsInDir(departureArs, opp);
                BusRouteDto arrInOpp = findByArsInDir(arr.arsId, opp);
                if (depInOpp != null && arrInOpp != null) {
                    new AlertDialog.Builder(this)
                            .setMessage("현재 방면 기준 역방향입니다.\n" + opp + " 방면으로 전환할까요?")
                            .setPositiveButton("전환", (d, w) -> {
                                switchDirection(opp);
                                Toast.makeText(this, "방면을 전환했어요. 다시 도착 정류장을 선택하세요.", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("취소", null)
                            .show();
                } else {
                    Toast.makeText(this, "진행방향이 맞지 않습니다. 반대 방면을 확인하세요.", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // 3) 예약/즐겨찾기 다이얼로그
            String routeName   = getIntent().getStringExtra(EXTRA_ROUTE_NAME);
            String depName     = !TextUtils.isEmpty(departureName) ? departureName : dep.stationNm;
            String arrivalName = arr.stationNm;

            // 현재 선택 조합의 즐겨찾기 여부/ID
            final Long matchedFavId = findMatchedFavoriteId(
                    currentBusRouteId, currentDir, dep.station, arr.station
            );
            final boolean isFavoriteInit = (matchedFavId != null);

            // 서버 연동 가능한 다이얼로그 생성
            ReserveDialogFragment dialog = ReserveDialogFragment.newInstanceFull(
                    depName, arrivalName,
                    TextUtils.isEmpty(routeName) ? arr.busRouteNm : routeName,
                    currentBusRouteId, currentDir,
                    dep.station, dep.stationNm, dep.arsId,
                    arr.station, arr.stationNm, arr.arsId,
                    isFavoriteInit, matchedFavId
            );

            // 예약 + 즐겨찾기 변경 콜백
            dialog.setOnReserveListener(new ReserveDialogFragment.OnReserveListener() {
                @Override
                public void onReserveComplete(String a, String b, String r, boolean boardingAlarm, boolean dropOffAlarm) {
                    // 예약 API 호출
                    ReservationCreateRequest req = new ReservationCreateRequest();
                    req.routeId       = currentBusRouteId;
                    req.direction     = currentDir;
                    req.boardStopId   = dep.station;
                    req.boardStopName = dep.stationNm;
                    req.boardArsId    = dep.arsId;
                    req.destStopId    = arr.station;
                    req.destStopName  = arr.stationNm;
                    req.destArsId     = arr.arsId;
                    req.routeName     = arr.busRouteNm;

                    String access = TokenStore.getAccess(getApplicationContext());
                    String bearer = (access != null && !access.isEmpty()) ? ("Bearer " + access) : "";

                    Dialog pd = UiDialogs.showLoading(BusRouteActivity.this, "예약 중...");

                    ApiClient.get().createReservation(bearer, req).enqueue(new Callback<ReservationResponse>() {
                        @Override
                        public void onResponse(Call<ReservationResponse> call, Response<ReservationResponse> resp) {
                            pd.dismiss();

                            if (isFinishing() || isDestroyed()) return; // 액티비티 종료 중이면 UI 작업 안 함

                            if (resp.isSuccessful() && resp.body() != null) {
                                ReservationResponse body = resp.body();

                                getSharedPreferences("app", MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("JUST_RESERVED", true)
                                        .apply();

                                // 예쁜 카드형 완료 다이얼로그 (1초 후 자동 닫힘 + finish)
                                routeDialog = UiDialogs.showReservationDone(
                                        BusRouteActivity.this,
                                        "노선: " + (TextUtils.isEmpty(routeName) ? arr.busRouteNm : routeName),
                                        body.boardStopName + " → " + body.destStopName,
                                        1000L,
                                        () -> {
                                            if (!isFinishing() && !isDestroyed()) {
                                                finish();
                                            }
                                        }
                                );

                            } else {
                                int code = resp.code();
                                if (code == 401) {
                                    TokenStore.clearAccess(getApplicationContext());
                                    LoginRequiredDialogFragment.show(getSupportFragmentManager());
                                    return;
                                }
                                String msg =
                                        (code == 409) ? "예약 불가(중복/정책 위반). 다른 조합을 선택하세요."
                                                : (code == 422) ? "진행방향이 맞지 않습니다. 반대 방면을 확인하세요."
                                                : "예약 실패: HTTP " + code;
                                Toast.makeText(BusRouteActivity.this, msg, Toast.LENGTH_LONG).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<ReservationResponse> call, Throwable t) {
                            pd.dismiss();
                            if (isFinishing() || isDestroyed()) return;
                            Toast.makeText(BusRouteActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onFavoriteChanged(boolean isFav, Long favId) {
                    // 다이얼로그에서 서버 반영이 끝난 상태 → 캐시 재동기화
                    fetchFavoritesOnce();
                }
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

                allStops.clear();
                allStops.addAll(r.body());

                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (BusRouteDto s : allStops) set.add(s.direction);
                List<String> dirs = new ArrayList<>(set);
                dirLeft  = dirs.size() >= 1 ? dirs.get(0) : "방면 A";
                dirRight = dirs.size() >= 2 ? dirs.get(1) : dirLeft;

                tvLeft.setText(dirLeft + " 방면");
                tvRight.setText(dirRight + " 방면");

                String first = pickInitialDirectionByDepartureOrFocus(defaultDir, departureArs, focusStationId, focusSeq);
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
        if (focusArsId != null) {
            boolean inLeft = false, inRight = false;
            for (BusRouteDto s : allStops) {
                if (focusArsId.equals(s.arsId)) {
                    if (s.direction != null && s.direction.equals(dirLeft))  inLeft = true;
                    if (s.direction != null && s.direction.equals(dirRight)) inRight= true;
                }
            }
            if (inLeft ^ inRight) return inLeft ? dirLeft : dirRight;
        }

        if (focusStationId != null) {
            boolean inLeft = false, inRight = false;
            for (BusRouteDto s : allStops) {
                if (focusStationId.equals(s.station)) {
                    if (s.direction != null && s.direction.equals(dirLeft))  inLeft = true;
                    if (s.direction != null && s.direction.equals(dirRight)) inRight= true;
                }
            }
            if (inLeft ^ inRight) return inLeft ? dirLeft : dirRight;
        }

        if (focusSeq != null) {
            for (BusRouteDto s : allStops) {
                if (safeInt(s.seq, -1) == focusSeq) return s.direction;
            }
        }

        if (defaultDir != null && (defaultDir.equals(dirLeft) || defaultDir.equals(dirRight))) {
            return defaultDir;
        }
        return dirLeft;
    }

    private void switchDirection(String dir) {
        if (dir == null) return;
        currentDir = dir;

        if (dir.equals(dirLeft)) {
            updateTabStyle(0);
            animateTab(0);
        } else {
            updateTabStyle(1);
            animateTab(1);
        }

        List<BusRouteDto> filtered = new ArrayList<>();
        for (BusRouteDto s : allStops) if (dir.equals(s.direction)) filtered.add(s);

        adapter.submit(filtered);
        adapter.setDepartureArsId(departureArs);

        indexVisibleBySeq(filtered);
        currentDirectionStops.clear();
        currentDirectionStops.addAll(filtered);

        buildIndexForDirection(filtered);
        loadAndApplyBusLocations(currentBusRouteId);

        if (!TextUtils.isEmpty(departureArs)) {
            for (BusRouteDto s : filtered) {
                if (departureArs.equals(s.arsId)) {
                    Integer pos = adapter.findPosBySeq(safeInt(s.seq, -1));
                    if (pos != null) smoothCenterTo(pos);
                    return;
                }
            }
        }
        centerOnFocusIfAny(filtered);
    }

    private void centerOnFocusIfAny(List<BusRouteDto> list) {
        int pos = findFocusPosition(list);
        if (pos >= 0) {
            smoothCenterTo(pos);
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
        if (focusArsId != null) {
            for (int i = 0; i < list.size(); i++) {
                if (focusArsId.equals(list.get(i).arsId)) return i;
            }
        }
        return -1;
    }

    private void smoothCenterTo(int position) {
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) {
            rv.scrollToPosition(position);
            return;
        }
        LinearLayoutManager llm = (LinearLayoutManager) lm;

        rv.post(() -> {
            llm.scrollToPositionWithOffset(position, rv.getHeight() / 2);
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

    private void loadAndApplyBusLocations(String busRouteId) {
        ApiClient.get().getBusLocation(busRouteId).enqueue(new Callback<List<BusLocationDto>>() {
            @Override public void onResponse(Call<List<BusLocationDto>> c, Response<List<BusLocationDto>> r) {
                if (!r.isSuccessful() || r.body() == null) return;

                List<BusRouteAdapter.BusMark> marks = new ArrayList<>();
                Map<Integer,Integer> dup = new HashMap<>();

                for (BusLocationDto b : r.body()) {
                    Integer nextSeq = null;

                    if (b.sectionId != null) {
                        nextSeq = nextSeqBySection.get(b.sectionId.trim());
                    }

                    if (nextSeq == null && b.lastStnId != null) {
                        Integer lastSeq = lastSeqByStation.get(b.lastStnId.trim());
                        if (lastSeq != null) nextSeq = Math.min(lastSeq + 1, dirSeqMax);
                    }

                    if (nextSeq == null) continue;
                    if (nextSeq < dirSeqMin || nextSeq > dirSeqMax) continue;

                    boolean stopped = "1".equals(b.stopFlag);
                    int displaySeq;
                    if (stopped && b.lastStnId != null) {
                        Integer lastSeq = lastSeqByStation.get(b.lastStnId.trim());
                        displaySeq = (lastSeq != null) ? lastSeq : Math.max(dirSeqMin, nextSeq - 1);
                    } else {
                        displaySeq = nextSeq;
                    }
                    if (displaySeq < dirSeqMin || displaySeq > dirSeqMax) continue;

                    float t;
                    if (stopped) {
                        t = 1f;
                    } else if (b.fullSectDist != null && b.sectDist != null && b.fullSectDist > 0) {
                        t = (float)Math.max(0, Math.min(1, b.sectDist / b.fullSectDist));
                    } else {
                        t = 0.5f;
                    }

                    int n = dup.getOrDefault(displaySeq, 0);
                    dup.put(displaySeq, n + 1);
                    if (!stopped) t = Math.max(0f, Math.min(1f, t + 0.08f * n));

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
                lastSeqByStation.put(s.station, seq);
            }
            if (s.section != null) {
                String sec = s.section.trim();
                if (!sec.isEmpty()) nextSeqBySection.put(sec, seq);
            }
        }
        if (mn == Integer.MAX_VALUE) { mn = 1; mx = 1; }
        dirSeqMin = mn;
        dirSeqMax = mx;

        nextSeqBySection.putIfAbsent("0", dirSeqMin);
    }

    @Nullable
    private BusRouteDto findByArsInCurrentDir(@Nullable String arsId) {
        if (arsId == null) return null;
        for (BusRouteDto s : currentDirectionStops) {
            if (arsId.equals(s.arsId)) return s;
        }
        return null;
    }

    @Nullable
    private BusRouteDto findByArsInDir(@Nullable String arsId, @Nullable String dir) {
        if (arsId == null || dir == null) return null;
        for (BusRouteDto s : allStops) {
            if (dir.equals(s.direction) && arsId.equals(s.arsId)) return s;
        }
        return null;
    }

    @Nullable
    private String oppositeDirOf(@Nullable String d) {
        if (d == null) return null;
        if (d.equals(dirLeft))  return dirRight;
        if (d.equals(dirRight)) return dirLeft;
        return null;
    }

    private String pickInitialDirectionByDepartureOrFocus(@Nullable String defaultDir,
                                                          @Nullable String depArs,
                                                          @Nullable String focusStationId,
                                                          @Nullable Integer focusSeq) {
        if (!TextUtils.isEmpty(depArs)) {
            boolean inLeft = false, inRight = false;
            for (BusRouteDto s : allStops) {
                if (depArs.equals(s.arsId)) {
                    if (s.direction != null && s.direction.equals(dirLeft))  inLeft  = true;
                    if (s.direction != null && s.direction.equals(dirRight)) inRight = true;
                }
            }
            if (inLeft ^ inRight) return inLeft ? dirLeft : dirRight;
        }
        String byFocus = pickInitialDirection(defaultDir, focusStationId, focusSeq);
        if (byFocus != null) return byFocus;
        return (dirLeft != null) ? dirLeft : defaultDir;
    }

    // ========= 즐겨찾기 헬퍼 =========

    private void fetchFavoritesOnce() {
        String access = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(access)) {
            favList.clear();
            return;
        }
        ApiClient.get().getFavorites("Bearer " + access)
                .enqueue(new Callback<List<ApiService.FavoriteResponse>>() {
                    @Override
                    public void onResponse(Call<List<ApiService.FavoriteResponse>> call,
                                           Response<List<ApiService.FavoriteResponse>> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            favList.clear();
                            favList.addAll(res.body());
                        }
                    }
                    @Override
                    public void onFailure(Call<List<ApiService.FavoriteResponse>> call, Throwable t) { }
                });
    }

    @Nullable
    private Long findMatchedFavoriteId(String routeId, String direction,
                                       String boardStopId, String destStopId) {
        String dir = direction == null ? "" : direction;
        for (ApiService.FavoriteResponse f : favList) {
            String fdir = f.direction == null ? "" : f.direction;
            if (TextUtils.equals(f.routeId, routeId)
                    && TextUtils.equals(fdir, dir)
                    && TextUtils.equals(f.boardStopId, boardStopId)
                    && TextUtils.equals(f.destStopId, destStopId)) {
                return f.id;
            }
        }
        return null;
    }

    // 아래 두 메서드는 현재는 사용되지 않지만,
    // 필요 시 Activity에서 직접 즐겨찾기 조작할 때 재사용 가능하도록 남겨둠.
    private void addFavoriteForSelection(String routeId, String direction,
                                         String boardStopId, String boardStopName, String boardArsId,
                                         String destStopId, String destStopName, String destArsId,
                                         String routeName,
                                         @Nullable Runnable onDone) {
        String access = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(access)) {
            LoginRequiredDialogFragment.show(getSupportFragmentManager());
            if (onDone != null) onDone.run();
            return;
        }

        ApiService.FavoriteCreateRequest body = new ApiService.FavoriteCreateRequest(
                routeId, direction,
                boardStopId, boardStopName, boardArsId,
                destStopId, destStopName, destArsId,
                routeName
        );

        ApiClient.get().addFavorite("Bearer " + access, body)
                .enqueue(new Callback<ApiService.FavoriteResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.FavoriteResponse> call,
                                           Response<ApiService.FavoriteResponse> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            favList.add(res.body());
                            Toast.makeText(BusRouteActivity.this, "즐겨찾기에 추가되었습니다.", Toast.LENGTH_SHORT).show();
                        } else if (res.code() == 409) {
                            Toast.makeText(BusRouteActivity.this, "이미 즐겨찾기에 있습니다.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(BusRouteActivity.this, "즐겨찾기 추가 실패 (" + res.code() + ")", Toast.LENGTH_SHORT).show();
                        }
                        if (onDone != null) onDone.run();
                    }
                    @Override
                    public void onFailure(Call<ApiService.FavoriteResponse> call, Throwable t) {
                        Toast.makeText(BusRouteActivity.this, "네트워크 오류로 추가 실패", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    }
                });
    }

    private void deleteFavoriteById(@Nullable Long favId, @Nullable Runnable onDone) {
        if (favId == null) { if (onDone != null) onDone.run(); return; }

        String access = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(access)) {
            LoginRequiredDialogFragment.show(getSupportFragmentManager());
            if (onDone != null) onDone.run();
            return;
        }

        ApiClient.get().deleteFavorite("Bearer " + access, favId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> res) {
                        // 로컬 캐시에서 제거
                        for (int i = favList.size() - 1; i >= 0; i--) {
                            if (favList.get(i).id.equals(favId)) {
                                favList.remove(i);
                            }
                        }
                        Toast.makeText(BusRouteActivity.this, "즐겨찾기에서 제거되었습니다.", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(BusRouteActivity.this, "네트워크 오류로 제거 실패", Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        // WindowLeaked 방지: 살아있는 다이얼로그 안전 종료
        if (routeDialog != null && routeDialog.isShowing()) {
            routeDialog.dismiss();
        }
        routeDialog = null;
        super.onDestroy();
    }
}

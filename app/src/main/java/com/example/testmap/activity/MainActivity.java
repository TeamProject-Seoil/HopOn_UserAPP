package com.example.testmap.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout; // (미사용이면 삭제 가능)
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.testmap.R;
import com.example.testmap.dto.StationDto;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.ui.ArrivalsBottomSheet;
import com.example.testmap.util.TokenStore;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    // ───────── UI/Map 필드 ─────────
    private DrawerLayout drawerLayout;
    private MapView mapView;
    private ImageButton menuButton, favoriteButton;
    private View layoutMenu, layoutFavorites;
    private NaverMap naverMap;

    // ───────── 위치 권한/소스 ─────────
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource; // 기본: 구글 Fused 위치 제공자 사용

    // ───────── 이동 기반 재조회 파라미터 ─────────
    private static final int   RADIUS_M            = 1000;   // 정류장 검색 반경(m)
    private static final float MIN_MOVE_METERS     = 25f;    // 이만큼 이동해야 재조회
    private static final long  MIN_INTERVAL_MS     = 7_000L; // 재조회 최소 간격(ms)
    private static final float ACCURACY_MAX_METERS = 20f;    // 정확도 상한(이보다 나쁘면 무시)

    // ───────── 위치/조회 상태 ─────────
    private LatLng lastFix = null;               // 최신 위치
    private LatLng lastFetchedCenter = null;     // 마지막 API 호출 기준 좌표
    private long   lastFetchAt = 0L;             // 마지막 호출 시각
    private boolean firstFixAccepted = false;    // 첫 위치 콜백은 정확도 무시하고 수락

    // ───────── 정류장 마커 ─────────
    private final List<Marker> stationMarkers = new ArrayList<>();

    // ───────── 로그인 UI ─────────
    private Button loginButton, registerButton;
    private View userPanel;
    private ImageView imageProfile;
    private TextView textUserName;
    private Button btnLogout;

    private static final String CLIENT_TYPE = "USER_APP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 시스템 inset 적용(상단바/하단바 패딩)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // MapView 초기화 + 비동기 준비
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState); // <- onCreate 꼭 호출
        mapView.getMapAsync(this);            // <- 지도가 준비되면 onMapReady 콜백

        // 드로어/버튼 바인딩
        drawerLayout = findViewById(R.id.main);
        menuButton = findViewById(R.id.menu_button);
        favoriteButton = findViewById(R.id.favorite_button);
        layoutMenu = findViewById(R.id.activity_menu);
        layoutFavorites = findViewById(R.id.activity_favorites);

        // 위치 소스 준비(권한 결과는 onRequestPermissionsResult 통해 전달)
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        // 왼쪽 상단 메뉴 버튼
        if (menuButton != null) {
            menuButton.setOnClickListener(view -> {
                layoutFavorites.setVisibility(View.GONE);
                layoutMenu.setVisibility(View.VISIBLE);
                drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        // 즐겨찾기 버튼
        if (favoriteButton != null) {
            favoriteButton.setOnClickListener(view -> {
                layoutMenu.setVisibility(View.GONE);
                layoutFavorites.setVisibility(View.VISIBLE);
                drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        // 최초 실행 시 권한 요청(실패 시 onRequestPermissionsResult로 회수)
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );

        // 시작 시 드로어 닫기
        drawerLayout.closeDrawer(GravityCompat.START);

        // 메뉴/로그인 섹션 바인딩
        MenuLayout();

        // (선택) GMS 상태 점검 로그 — Fused가 아예 못 돌 경우 디버깅에 도움
        // int gmsCode = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        //        .isGooglePlayServicesAvailable(this);
        // android.util.Log.d("GMS", "PlayServices status=" + gmsCode); // 0=SUCCESS
    }

    private void MenuLayout() {
        // 기본은 메뉴 탭만 보이게
        layoutMenu.setVisibility(View.VISIBLE);
        layoutFavorites.setVisibility(View.GONE);

        // 로그인/회원가입 버튼
        loginButton = findViewById(R.id.login_button);
        registerButton = findViewById(R.id.register_button);
        if (loginButton != null)
            loginButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LoginActivity.class)));
        if (registerButton != null)
            registerButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, RegisterActivity.class)));

        // 로그인 후 사용자 패널(프로필/닉네임/로그아웃)
        userPanel    = findViewById(R.id.user_panel);
        imageProfile = findViewById(R.id.image_profile);
        textUserName = findViewById(R.id.text_user_name);
        btnLogout    = findViewById(R.id.btn_logout);
        if (btnLogout != null) btnLogout.setOnClickListener(v -> doLogout());

        // 공지/설정/오픈소스 버튼
        View menuSection = findViewById(R.id.menu_section);
        if (menuSection != null) {
            Button noticeButton = menuSection.findViewById(R.id.btn_notice);
            if (noticeButton != null)
                noticeButton.setOnClickListener(v -> {
                    startActivity(new Intent(MainActivity.this, NoticeActivity.class));
                    drawerLayout.closeDrawer(GravityCompat.START);
                });

            Button settingsButton = menuSection.findViewById(R.id.btn_settings);
            if (settingsButton != null)
                settingsButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

            Button opensourceButton = menuSection.findViewById(R.id.btn_opensource);
            if (opensourceButton != null)
                opensourceButton.setOnClickListener(v -> {
                    // TODO: 오픈소스 화면 이동
                });
        }
    }

    // ───────── MapView 라이프사이클 연동(중요) ─────────
    @Override protected void onStart()  { super.onStart();  mapView.onStart(); }
    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();

        // 로그인 상태 반영
        renderHeaderByAuth();

        // 권한/설정 상태에 따라 내 위치 표시/트래킹을 '항상' 한 번 더 보장
        ensureLocationTracking();
    }
    @Override protected void onPause()  { super.onPause();  mapView.onPause(); }
    @Override protected void onStop()   { super.onStop();   mapView.onStop(); }
    @Override protected void onDestroy(){ super.onDestroy();mapView.onDestroy(); }

    // (권장) 회전/프로세스 재생성 대비 저장
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); } // MapView 지원 시 호출

    // 권한 응답 처리(FusedLocationSource에게 먼저 위임)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            // 권한 허용/거부 후 현재 상태에 맞춰 트래킹/오버레이 재설정
            ensureLocationTracking();
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // ───────── 주변 정류장 API 호출/렌더 ─────────
    private void fetchNearStations(double x, double y, int radius) {
        // 주의: 서버 파라미터가 (x=경도, y=위도) 순서라고 가정함
        ApiClient.get().getNearStations(x, y, radius).enqueue(new retrofit2.Callback<List<StationDto>>() {
            @Override public void onResponse(retrofit2.Call<List<StationDto>> call,
                                             retrofit2.Response<List<StationDto>> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    android.util.Log.e("API", "nearstations HTTP " + res.code());
                    return;
                }
                android.util.Log.d("API", "nearstations size=" + res.body().size());
                renderStationMarkers(res.body());
            }
            @Override public void onFailure(retrofit2.Call<List<StationDto>> call, Throwable t) {
                android.util.Log.e("API", "nearstations failed", t);
            }
        });
    }

    private void renderStationMarkers(List<StationDto> stations) {
        // 기존 마커 제거
        for (Marker m : stationMarkers) m.setMap(null);
        stationMarkers.clear();

        for (StationDto s : stations) {
            if (s.arsId == null || s.arsId.equals("0")) continue;

            Marker m = new Marker();
            // LatLng 생성자는 (위도, 경도)이므로 y=lat, x=lon 순서로 넣는다
            m.setPosition(new LatLng(s.y, s.x));
            m.setCaptionText(s.stationName);
            m.setIcon(OverlayImage.fromResource(R.drawable.mapmark)); // 커스텀 아이콘
            m.setMap(naverMap);
            stationMarkers.add(m);

            // 마커 클릭 시 도착정보 BottomSheet 표시
            m.setTag(s);
            m.setOnClickListener(overlay -> {
                StationDto st = (StationDto) overlay.getTag();
                ArrivalsBottomSheet sheet = ArrivalsBottomSheet.newInstance(st.arsId, st.stationName);
                sheet.show(getSupportFragmentManager(), "arrivals");
                return true;
            });
        }
    }

    // 두 좌표 사이 거리(m)
    private static float distanceMeters(LatLng a, LatLng b) {
        if (a == null || b == null) return Float.MAX_VALUE;
        float[] out = new float[1];
        android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out);
        return out[0];
    }

    // ───────── 지도 준비 콜백 ─────────
    @Override
    public void onMapReady(@NonNull NaverMap map) {
        this.naverMap = map;

        // 지도 UI/레이어 기본값
        naverMap.setMapType(NaverMap.MapType.Basic);
        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRAFFIC, false);
        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRANSIT, false);

        // 위치 소스/내비 버튼 연결
        naverMap.setLocationSource(locationSource);
        naverMap.getUiSettings().setLocationButtonEnabled(true);

        // 권한/기기 위치 상태에 맞춰 즉시 Follow + 오버레이 visible 보장
        ensureLocationTracking();

        // 위치 변경 콜백 등록(첫 fix는 정확도 무시, 이후 20m 이하만 반영)
        hookLocationCallback();

        // (디버깅) 오버레이 렌더 자체 테스트가 필요하면 아래 주석 해제
        // naverMap.getLocationOverlay().setPosition(new LatLng(37.5665, 126.9780)); // 서울시청
        // naverMap.getLocationOverlay().setVisible(true);
    }

    // ───────── 위치 활성/안내 ─────────
    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void promptTurnOnLocationIfNeeded() {
        Toast.makeText(this, "기기 위치가 꺼져 있어요. 켜야 현재 위치를 표시할 수 있어요.", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    // ───────── 위치 트래킹/오버레이 보장 ─────────
    private void ensureLocationTracking() {
        if (naverMap == null) return;

        boolean fineGranted =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        // 권한 없음 → 요청하고, 일단 표시/트래킹 끔
        if (!fineGranted && !coarseGranted) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
            naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            naverMap.getLocationOverlay().setVisible(false);
            return;
        }

        // 권한은 있는데 '기기 위치'가 꺼져 있음 → 안내/비표시
        if (!isLocationEnabled()) {
            naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            naverMap.getLocationOverlay().setVisible(false);
            promptTurnOnLocationIfNeeded();
            return;
        }

        // 여기까지 왔으면 실제 표시를 보장
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow); // 카메라가 내 위치 추적
        naverMap.getLocationOverlay().setVisible(true);                // 파란점(내 위치) 보이기
    }

    // ───────── 위치 변경 콜백(재조회 트리거/필터) ─────────
    private void hookLocationCallback() {
        if (naverMap == null) return;

        naverMap.addOnLocationChangeListener(location -> {
            if (location == null) return;

            // 디버깅: 콜백이 실제로 오는지/정확도는 어떤지 확인
            android.util.Log.d("LOC", "fix=" + location.getLatitude() + "," + location.getLongitude()
                    + " acc=" + (location.hasAccuracy() ? location.getAccuracy() : -1));

            // 첫 콜백은 정확도 무시하고 수락(초기 로딩 빠르게)
            if (!firstFixAccepted) {
                firstFixAccepted = true;
            } else {
                // 이후부터는 정확도 20m 이하만 반영
                if (location.hasAccuracy() && location.getAccuracy() > ACCURACY_MAX_METERS) {
                    return;
                }
            }

            LatLng now = new LatLng(location.getLatitude(), location.getLongitude());
            lastFix = now;

            long nowMs = System.currentTimeMillis();
            // 최초 1회 즉시 조회
            if (lastFetchedCenter == null) {
                fetchNearStations(now.longitude, now.latitude, RADIUS_M); // 서버는 (lon, lat) 순서
                lastFetchedCenter = now;
                lastFetchAt = nowMs;
                return;
            }
            // 최소 간격 미만이면 무시
            if (nowMs - lastFetchAt < MIN_INTERVAL_MS) return;

            // lastFetchedCenter 로부터 25m 이상 이동한 경우에만 재조회
            if (distanceMeters(lastFetchedCenter, now) >= MIN_MOVE_METERS) {
                fetchNearStations(now.longitude, now.latitude, RADIUS_M);
                lastFetchedCenter = now;
                lastFetchAt = nowMs;
            }
        });
    }

    // ───────── 로그인 상태 렌더링 ─────────
    private void renderHeaderByAuth() {
        String access = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(access)) {
            tryRefreshThenRender();
        } else {
            fetchMeAndRender("Bearer " + access, true);
        }
    }

    private void tryRefreshThenRender() {
        String refresh = TokenStore.getRefresh(this);
        if (TextUtils.isEmpty(refresh)) { showLoggedOutUi(); return; }

        ApiService api = ApiClient.get();
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        api.refresh(refresh, CLIENT_TYPE, deviceId)
                .enqueue(new retrofit2.Callback<ApiService.AuthResponse>() {
                    @Override public void onResponse(retrofit2.Call<ApiService.AuthResponse> call,
                                                     retrofit2.Response<ApiService.AuthResponse> res) {
                        if (res.isSuccessful() && res.body()!=null) {
                            TokenStore.saveAccess(MainActivity.this, res.body().accessToken);
                            TokenStore.saveRefresh(MainActivity.this, res.body().refreshToken);
                            fetchMeAndRender("Bearer " + res.body().accessToken, false);
                        } else {
                            showLoggedOutUi();
                        }
                    }
                    @Override public void onFailure(retrofit2.Call<ApiService.AuthResponse> call, Throwable t) {
                        showLoggedOutUi();
                    }
                });
    }

    private void fetchMeAndRender(String bearer, boolean allowRefresh) {
        ApiClient.get().me(bearer).enqueue(new retrofit2.Callback<ApiService.UserResponse>() {
            @Override public void onResponse(retrofit2.Call<ApiService.UserResponse> call,
                                             retrofit2.Response<ApiService.UserResponse> res) {
                if (res.isSuccessful() && res.body()!=null) {
                    showLoggedInUi(bearer, res.body());
                } else if (res.code()==401 && allowRefresh) {
                    tryRefreshThenRender();
                } else {
                    showLoggedOutUi();
                }
            }
            @Override public void onFailure(retrofit2.Call<ApiService.UserResponse> call, Throwable t) {
                showLoggedOutUi();
            }
        });
    }

    private void showLoggedInUi(String bearer, ApiService.UserResponse me) {
        if (loginButton != null)    loginButton.setVisibility(View.GONE);
        if (registerButton != null) registerButton.setVisibility(View.GONE);
        if (userPanel != null)      userPanel.setVisibility(View.VISIBLE);
        if (textUserName != null)   textUserName.setText(me.username != null ? me.username : me.userid);

        if (btnLogout != null) btnLogout.setOnClickListener(v -> doLogout());

        if (me.hasProfileImage && imageProfile != null) {
            loadProfileImage(bearer);
        } else if (imageProfile != null) {
            imageProfile.setImageResource(R.drawable.ic_account_circle);
        }
    }

    private void showLoggedOutUi() {
        if (userPanel != null)      userPanel.setVisibility(View.GONE);
        if (loginButton != null)    loginButton.setVisibility(View.VISIBLE);
        if (registerButton != null) registerButton.setVisibility(View.VISIBLE);
    }

    private void loadProfileImage(String bearer) {
        ApiClient.get().meImage(bearer).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override public void onResponse(retrofit2.Call<ResponseBody> call,
                                             retrofit2.Response<ResponseBody> res) {
                if (!res.isSuccessful() || res.body()==null || imageProfile==null) return;
                imageProfile.setImageBitmap(BitmapFactory.decodeStream(res.body().byteStream()));
            }
            @Override public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) { /* ignore */ }
        });
    }

    private void doLogout() {
        String refresh = TokenStore.getRefresh(this);
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        if (TextUtils.isEmpty(refresh)) {
            TokenStore.clearAccess(this);
            TokenStore.clearRefresh(this);
            showLoggedOutUi();
            Toast.makeText(this, "로그아웃 되었습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService.LogoutRequest body = new ApiService.LogoutRequest(CLIENT_TYPE, deviceId, refresh);
        ApiClient.get().logout(body).enqueue(new retrofit2.Callback<Void>() {
            @Override public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> res) {
                TokenStore.clearAccess(MainActivity.this);
                TokenStore.clearRefresh(MainActivity.this);
                showLoggedOutUi();
                Toast.makeText(MainActivity.this, "로그아웃 되었습니다", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                TokenStore.clearAccess(MainActivity.this);
                TokenStore.clearRefresh(MainActivity.this);
                showLoggedOutUi();
            }
        });
    }
}

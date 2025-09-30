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

/**
 * 앱 메인 화면
 * - 네이버 지도 표시
 * - 주변 정류장 마커 표시
 * - 사이드 메뉴 (로그인/회원가입/설정/공지사항 등)
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    // Drawer + 지도 관련
    private DrawerLayout drawerLayout;
    private MapView mapView;
    private ImageButton menuButton, favoriteButton;
    private View layoutMenu, layoutFavorites;
    private NaverMap naverMap;

    // 위치 권한 요청 코드
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    // 위치 업데이트 관련 설정값
    private static final int   RADIUS_M            = 1000;   // 정류장 검색 반경(m)
    private static final float MIN_MOVE_METERS     = 25f;    // 이만큼 이동해야 재조회
    private static final long  MIN_INTERVAL_MS     = 7_000L; // 재조회 최소 간격(ms)
    private static final float ACCURACY_MAX_METERS = 20f;    // 정확도 상한(이보다 나쁘면 무시)

    // 위치 상태
    private LatLng lastFix = null;               // 최신 위치
    private LatLng lastFetchedCenter = null;     // 마지막 API 호출 기준 좌표
    private long   lastFetchAt = 0L;             // 마지막 호출 시각
    private boolean firstFixAccepted = false;    // 첫 위치 콜백은 정확도 무시하고 수락

    // 정류장 마커 리스트
    private final List<Marker> stationMarkers = new ArrayList<>();

    // 메뉴 영역 - 로그인/회원 UI
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

        // 상태바/네비게이션바 여백 반영
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 지도 초기화
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState); // <- onCreate 꼭 호출
        mapView.getMapAsync(this);            // <- 지도가 준비되면 onMapReady 콜백

        // Drawer 초기화
        drawerLayout = findViewById(R.id.main);
        menuButton = findViewById(R.id.menu_button);
        favoriteButton = findViewById(R.id.favorite_button);
        layoutMenu = findViewById(R.id.activity_menu);
        layoutFavorites = findViewById(R.id.activity_favorites);

        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        // 메뉴 버튼 클릭
        if (menuButton != null) {
            menuButton.setOnClickListener(view -> {
                layoutFavorites.setVisibility(View.GONE);
                layoutMenu.setVisibility(View.VISIBLE);
                drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        // 즐겨찾기 버튼 클릭
        if (favoriteButton != null) {
            favoriteButton.setOnClickListener(view -> {
                layoutMenu.setVisibility(View.GONE);
                layoutFavorites.setVisibility(View.VISIBLE);
                drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        // 위치 권한 요청
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );

        drawerLayout.closeDrawer(GravityCompat.START);

        // 메뉴 초기화
        MenuLayout();

    }

    /** 메뉴 버튼 영역 초기화 */
    private void MenuLayout() {
        layoutMenu.setVisibility(View.VISIBLE);
        layoutFavorites.setVisibility(View.GONE);

        loginButton = findViewById(R.id.login_button);
        registerButton = findViewById(R.id.register_button);
        if (loginButton != null)
            loginButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LoginActivity.class)));
        if (registerButton != null)
            registerButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, RegisterActivity.class)));

        userPanel    = findViewById(R.id.user_panel);
        imageProfile = findViewById(R.id.image_profile);
        textUserName = findViewById(R.id.text_user_name);
        btnLogout    = findViewById(R.id.btn_logout);
        if (btnLogout != null) btnLogout.setOnClickListener(v -> doLogout());

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

    // ===== 생명주기 (MapView) =====
    @Override protected void onStart()  { super.onStart();  mapView.onStart(); }
    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        renderHeaderByAuth();
        ensureLocationTracking();
    }
    @Override protected void onPause()  { super.onPause();  mapView.onPause(); }
    @Override protected void onStop()   { super.onStop();   mapView.onStop(); }
    @Override protected void onDestroy(){ super.onDestroy();mapView.onDestroy(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }

    // ===== 위치 권한 처리 =====
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            ensureLocationTracking();
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // ===== 정류장 API 호출 =====
    private void fetchNearStations(double x, double y, int radius) {
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

    /** 지도에 정류장 마커 표시 */
    private void renderStationMarkers(List<StationDto> stations) {
        for (Marker m : stationMarkers) m.setMap(null);
        stationMarkers.clear();

        for (StationDto s : stations) {
            if (s.arsId == null || s.arsId.equals("0")) continue;

            Marker m = new Marker();
            m.setPosition(new LatLng(s.y, s.x));
            m.setCaptionText(s.stationName);
            m.setIcon(OverlayImage.fromResource(R.drawable.mapmark));
            m.setMap(naverMap);
            stationMarkers.add(m);

            // ★ StationDto를 꼭 태그로 심어두기
            m.setTag(s);

            // ★ 마커 클릭: 바텀시트만 띄움 (여기서 BusRouteActivity로 바로 가지 않음)
            m.setOnClickListener(overlay -> {
                StationDto st = (StationDto) overlay.getTag();
                ArrivalsBottomSheet f = ArrivalsBottomSheet.newInstance(
                        st.arsId,
                        st.stationName
                );
                f.show(getSupportFragmentManager(), "arrivals");
                return true;
            });
        }
    }



    /** 두 위치 간 거리(m) 계산 */
    private static float distanceMeters(LatLng a, LatLng b) {
        if (a == null || b == null) return Float.MAX_VALUE;
        float[] out = new float[1];
        android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out);
        return out[0];
    }

    // ===== 지도 준비 완료 =====
    @Override
    public void onMapReady(@NonNull NaverMap map) {
        this.naverMap = map;

        naverMap.setMapType(NaverMap.MapType.Basic);
        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRAFFIC, false);
        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRANSIT, false);

        naverMap.setLocationSource(locationSource);
        naverMap.getUiSettings().setLocationButtonEnabled(true);

        ensureLocationTracking();
        hookLocationCallback();
    }

    /** 기기 GPS 활성 여부 */
    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    /** 위치 꺼져있으면 안내 */
    private void promptTurnOnLocationIfNeeded() {
        Toast.makeText(this, "기기 위치가 꺼져 있어요. 켜야 현재 위치를 표시할 수 있어요.", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    /** 지도에 현재 위치 표시/추적 */
    private void ensureLocationTracking() {
        if (naverMap == null) return;

        boolean fineGranted =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

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

        if (!isLocationEnabled()) {
            naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            naverMap.getLocationOverlay().setVisible(false);
            promptTurnOnLocationIfNeeded();
            return;
        }

        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        naverMap.getLocationOverlay().setVisible(true);
    }

    /** 위치 변경 이벤트 수신 */
    private void hookLocationCallback() {
        if (naverMap == null) return;

        naverMap.addOnLocationChangeListener(location -> {
            if (location == null) return;

            if (!firstFixAccepted) {
                firstFixAccepted = true;
            } else {
                if (location.hasAccuracy() && location.getAccuracy() > ACCURACY_MAX_METERS) {
                    return;
                }
            }

            LatLng now = new LatLng(location.getLatitude(), location.getLongitude());
            lastFix = now;

            long nowMs = System.currentTimeMillis();
            if (lastFetchedCenter == null) {
                fetchNearStations(now.longitude, now.latitude, RADIUS_M);
                lastFetchedCenter = now;
                lastFetchAt = nowMs;
                return;
            }
            if (nowMs - lastFetchAt < MIN_INTERVAL_MS) return;

            if (distanceMeters(lastFetchedCenter, now) >= MIN_MOVE_METERS) {
                fetchNearStations(now.longitude, now.latitude, RADIUS_M);
                lastFetchedCenter = now;
                lastFetchAt = nowMs;
            }
        });
    }

    // ===== 인증 상태에 따른 UI =====
    private void renderHeaderByAuth() {
        String access = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(access)) {
            tryRefreshThenRender();
        } else {
            fetchMeAndRender("Bearer " + access, true);
        }
    }

    /** refreshToken 으로 갱신 */
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

    /** 사용자 정보 조회 */
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

    /** 로그인 된 상태 UI */
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

    /** 로그아웃 상태 UI */
    private void showLoggedOutUi() {
        if (userPanel != null)      userPanel.setVisibility(View.GONE);
        if (loginButton != null)    loginButton.setVisibility(View.VISIBLE);
        if (registerButton != null) registerButton.setVisibility(View.VISIBLE);
    }

    /** 프로필 이미지 로드 */
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

    /** 로그아웃 */
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

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
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.adapter.RecentAdapter;
import com.example.testmap.adapter.FavoriteAdapter;
import com.example.testmap.dto.ReservationResponse;
import com.example.testmap.dto.StationDto;
import com.example.testmap.model.CancelResult;
import com.example.testmap.model.FavoriteItem;
import com.example.testmap.model.RecentItem;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.ui.ArrivalsBottomSheet;
import com.example.testmap.util.TokenStore;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
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
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

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
    private static final float ACCURACY_MAX_METERS = 20f;    // 정확도 상한

    // 위치 상태
    private LatLng lastFix = null;
    private LatLng lastFetchedCenter = null;
    private long   lastFetchAt = 0L;
    private boolean firstFixAccepted = false;

    // 정류장 마커 리스트
    private final List<Marker> stationMarkers = new ArrayList<>();

    // 메뉴 영역 - 로그인/회원 UI
    private Button loginButton, registerButton;
    private View userPanel;
    private ImageView imageProfile;
    private TextView textUserName;
    private Button btnLogout;

    private static final String CLIENT_TYPE = "USER_APP";

    // 예약 바텀 시트
    private View bottomSheet;
    private BottomSheetBehavior<View> bottomBehavior;

    private TextView tvRoute, tvDir, tvFrom, tvTo;
    private View btnCancel;

    // 예약 취소
    private Long currentReservationId = null;

    // 태그
    private static final String TAG_ARRIVALS_SHEET = "arrivals";

    private boolean hasActiveReservation = false;

    // 즐겨찾기 및 최근내역
    private final List<RecentItem> recentList = new ArrayList<>();
    private final List<FavoriteItem> favList = new ArrayList<>();
    private RecentAdapter recentAdapter;
    private FavoriteAdapter favoriteAdapter;

    private TextView emptyText, recentEmptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 바텀시트
        bottomSheet = findViewById(R.id.bottom_sheet_layout);
        bottomBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomBehavior.setHideable(true);
        bottomBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        try { bottomBehavior.setDraggable(true); } catch (Throwable ignore) {}

        // 시스템 바 여백
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 지도
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Drawer
        drawerLayout = findViewById(R.id.main);
        menuButton = findViewById(R.id.menu_button);
        favoriteButton = findViewById(R.id.favorite_button);
        layoutMenu = findViewById(R.id.activity_menu);
        layoutFavorites = findViewById(R.id.activity_favorites);

        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        if (menuButton != null) {
            menuButton.setOnClickListener(view -> {
                if (layoutFavorites != null) layoutFavorites.setVisibility(View.GONE);
                if (layoutMenu != null) layoutMenu.setVisibility(View.VISIBLE);
                drawerLayout.openDrawer(GravityCompat.START);
            });
        }
        if (favoriteButton != null) {
            favoriteButton.setOnClickListener(view -> {
                if (layoutMenu != null) layoutMenu.setVisibility(View.GONE);
                if (layoutFavorites != null) layoutFavorites.setVisibility(View.VISIBLE);
                drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        // 위치 권한
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );

        drawerLayout.closeDrawer(GravityCompat.START);

        // 메뉴 초기화
        MenuLayout();

        // 즐겨찾기/최근내역
        emptyText = findViewById(R.id.emptyText);
        recentEmptyText = findViewById(R.id.recentEmptyText);

        RecyclerView recentRecycler = findViewById(R.id.recent_recycler);
        RecyclerView favRecycler = findViewById(R.id.fav_recycler);

        recentAdapter = new RecentAdapter(recentList);
        favoriteAdapter = new FavoriteAdapter(favList);

        recentRecycler.setLayoutManager(new LinearLayoutManager(this));
        favRecycler.setLayoutManager(new LinearLayoutManager(this));

        recentRecycler.setAdapter(recentAdapter);
        favRecycler.setAdapter(favoriteAdapter);

        recentAdapter.notifyDataSetChanged();
        favoriteAdapter.notifyDataSetChanged();

        updateEmptyText(emptyText, recentEmptyText);

        initBottomSheetViews();
        initFavoritesView();

        // 하드웨어 뒤로가기 우선순위
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                if (bottomBehavior != null &&
                        bottomBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    return;
                }
                Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_ARRIVALS_SHEET);
                if (f instanceof androidx.fragment.app.DialogFragment df) {
                    df.dismissAllowingStateLoss();
                    return;
                }
                setEnabled(false);
                MainActivity.super.onBackPressed(); // 안전하게 종료
            }
        });

        // 초기 UI 정합성 보장
        enforceMainUiState();
    }

    // 더미데이터 (원하면 호출)
    private void addDummyData() {
        recentList.clear();
        favList.clear();
        for (int i = 1; i <= 3; i++) {
            recentList.add(new RecentItem("최근 버스 " + i, "정류장 A" + i, "정류장 B" + i));
            favList.add(new FavoriteItem("즐겨찾기 버스 " + i, "울마삼 → 사가정 " + i, "평일 운행 / 배차간격 10분"));
        }
        recentAdapter.notifyDataSetChanged();
        favoriteAdapter.notifyDataSetChanged();
        updateEmptyText(emptyText, recentEmptyText);
    }

    private void initFavoritesView() {
        RecyclerView favRecycler = layoutFavorites.findViewById(R.id.fav_recycler);
        RecyclerView recentRecycler = layoutFavorites.findViewById(R.id.recent_recycler);
        TextView emptyText = layoutFavorites.findViewById(R.id.emptyText);
        TextView recentEmptyText = layoutFavorites.findViewById(R.id.recentEmptyText);
        View clearAll = layoutFavorites.findViewById(R.id.clearAll);

        recentAdapter = new RecentAdapter(recentList);
        favoriteAdapter = new FavoriteAdapter(favList);

        favRecycler.setLayoutManager(new LinearLayoutManager(this));
        recentRecycler.setLayoutManager(new LinearLayoutManager(this));
        favRecycler.setAdapter(favoriteAdapter);
        recentRecycler.setAdapter(recentAdapter);

        for (int i = 1; i <= 3; i++) {
            recentList.add(new RecentItem("버스 " + i, "정류장 A" + i, "정류장 B" + i));
            favList.add(new FavoriteItem("즐겨찾기 " + i, "사가정 방면", "배차간격 12분"));
        }

        recentAdapter.notifyDataSetChanged();
        favoriteAdapter.notifyDataSetChanged();

        updateEmptyText(emptyText, recentEmptyText);

        recentAdapter.setOnItemClickListener(new RecentAdapter.OnItemClickListener() {
            @Override public void onItemClick(RecentItem item) { /* TODO */ }

            @Override public void onAddFavClick(RecentItem item) {
                favList.add(new FavoriteItem(
                        item.getBusNumber(),
                        item.getStartStation() + " → " + item.getEndStation(),
                        "최근 추가된 즐겨찾기"
                ));
                favoriteAdapter.notifyDataSetChanged();
                updateEmptyText(emptyText, recentEmptyText);
            }
        });

        clearAll.setOnClickListener(v -> {
            favList.clear();
            favoriteAdapter.notifyDataSetChanged();
            updateEmptyText(emptyText, recentEmptyText);
        });
    }

    private void updateEmptyText(TextView emptyText, TextView recentEmptyText) {
        emptyText.setVisibility(favList.isEmpty() ? View.VISIBLE : View.GONE);
        recentEmptyText.setVisibility(recentList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 메뉴 버튼 영역 초기화 */
    private void MenuLayout() {
        if (layoutMenu != null)      layoutMenu.setVisibility(View.VISIBLE);
        if (layoutFavorites != null) layoutFavorites.setVisibility(View.GONE);

        // 뒤로가기(닫기) 아이콘
        ImageView backIconMenu = layoutMenu.findViewById(R.id.back_icon);
        if (backIconMenu != null) {
            backIconMenu.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));
        }
        ImageView backIconFav = layoutFavorites.findViewById(R.id.back_icon);
        if (backIconFav != null) {
            backIconFav.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));
        }

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
            // 줄 전체 클릭으로 이동 처리
            View rowSettings = menuSection.findViewById(R.id.row_settings);
            if (rowSettings != null) {
                rowSettings.setOnClickListener(v -> {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    drawerLayout.closeDrawer(GravityCompat.START);
                });
            }

            View rowNotice = menuSection.findViewById(R.id.row_notice);
            if (rowNotice != null) {
                rowNotice.setOnClickListener(v -> {
                    startActivity(new Intent(MainActivity.this, NoticeActivity.class));
                    drawerLayout.closeDrawer(GravityCompat.START);
                });
            }

            View rowOpensource = menuSection.findViewById(R.id.row_opensource);
            if (rowOpensource != null) {
                rowOpensource.setOnClickListener(v -> {
                    Toast.makeText(MainActivity.this, "오픈소스 활용정보는 준비 중입니다.", Toast.LENGTH_SHORT).show();
                    drawerLayout.closeDrawer(GravityCompat.START);
                });
            }
        }
    }

    // ===== 생명주기 (MapView) =====
    @Override protected void onStart()  { super.onStart();  mapView.onStart(); }
    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        renderHeaderByAuth();
        ensureLocationTracking();
        fetchAndShowActiveReservation();
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
    private void fetchNearStations(double longitude, double latitude, int radius) {
        ApiClient.get().getNearbyStations(longitude, latitude, radius)
                .enqueue(new retrofit2.Callback<List<StationDto>>() {
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

            // 태그로 데이터 심기
            m.setTag(s);

            // 마커 클릭: 도착정보 바텀시트
            m.setOnClickListener(overlay -> {
                if (hasActiveReservation) return true;
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

    /** 두 위치 간 거리(m) */
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
            } else if (location.hasAccuracy() && location.getAccuracy() > ACCURACY_MAX_METERS) {
                return;
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

    // ===== 인증 상태 UI =====
    private void renderHeaderByAuth() {
        String access = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(access)) {
            tryRefreshThenRender();
        } else {
            fetchMeAndRender("Bearer " + access, true);
        }
    }

    /** refreshToken 갱신 */
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

    /** 로그인 상태 UI */
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

    /** 로그아웃 (응답 타입을 Map으로 수정) */
    private void doLogout() {
        String refresh = TokenStore.getRefresh(this);
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        if (TextUtils.isEmpty(refresh)) {
            TokenStore.clearAccess(this);
            TokenStore.clearRefresh(this);
            showLoggedOutUi();
            Toast.makeText(this, "로그아웃 되었습니다", Toast.LENGTH_SHORT).show();
            updateReservationSheetVisibility(false, false);
            hasActiveReservation = false;
            currentReservationId = null;
            return;
        }

        ApiService.LogoutRequest body = new ApiService.LogoutRequest(CLIENT_TYPE, deviceId, refresh);
        ApiClient.get().logout(body).enqueue(new retrofit2.Callback<Map<String, Object>>() {
            @Override public void onResponse(retrofit2.Call<Map<String, Object>> call,
                                             retrofit2.Response<Map<String, Object>> res) {
                TokenStore.clearAccess(MainActivity.this);
                TokenStore.clearRefresh(MainActivity.this);
                showLoggedOutUi();
                Toast.makeText(MainActivity.this, "로그아웃 되었습니다", Toast.LENGTH_SHORT).show();
                updateReservationSheetVisibility(false, false);
                hasActiveReservation = false;
                currentReservationId = null;
            }
            @Override public void onFailure(retrofit2.Call<Map<String, Object>> call, Throwable t) {
                TokenStore.clearAccess(MainActivity.this);
                TokenStore.clearRefresh(MainActivity.this);
                showLoggedOutUi();
                updateReservationSheetVisibility(false, false);
                hasActiveReservation = false;
                currentReservationId = null;
            }
        });
    }

    // 예약 조회
    private void fetchAndShowActiveReservation() {
        String access = TokenStore.getAccess(getApplicationContext());
        if (TextUtils.isEmpty(access)) {
            updateReservationSheetVisibility(false, false);
            enforceMainUiState();
            return;
        }
        String bearer = "Bearer " + access;

        ApiClient.get().getActiveReservation(bearer).enqueue(new retrofit2.Callback<ReservationResponse>() {
            @Override public void onResponse(Call<ReservationResponse> call, Response<ReservationResponse> resp) {
                boolean justReserved = getSharedPreferences("app", MODE_PRIVATE)
                        .getBoolean("JUST_RESERVED", false);

                if (justReserved) {
                    Toast.makeText(MainActivity.this, "예약이 완료되었습니다.", Toast.LENGTH_SHORT).show();
                    getSharedPreferences("app", MODE_PRIVATE).edit().remove("JUST_RESERVED").apply();
                    bottomBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }

                if (resp.code() == 204) {
                    hasActiveReservation = false;
                    updateReservationSheetVisibility(true, false);
                    enforceMainUiState();
                    return;
                }
                if (resp.code() == 401) {
                    TokenStore.clearAccess(getApplicationContext());
                    hasActiveReservation = false;
                    updateReservationSheetVisibility(false, false);
                    enforceMainUiState();
                    return;
                }
                if (resp.isSuccessful() && resp.body() != null) {
                    ReservationResponse r = resp.body();
                    hasActiveReservation = true;
                    if (TextUtils.isEmpty(TokenStore.getAccess(getApplicationContext()))) {
                        updateReservationSheetVisibility(false, false);
                        return;
                    }
                    bindReservationDataToSheet(r);
                    dismissArrivalsSheetIfShown();
                    enforceMainUiState();
                    updateReservationSheetVisibility(true, true);
                } else {
                    updateReservationSheetVisibility(true, false);
                    hasActiveReservation = false;
                    enforceMainUiState();
                }
            }
            @Override public void onFailure(Call<ReservationResponse> call, Throwable t) {
                boolean loggedIn = !TextUtils.isEmpty(TokenStore.getAccess(getApplicationContext()));
                hasActiveReservation = false;
                updateReservationSheetVisibility(loggedIn, false);
                enforceMainUiState();
            }
        });
    }

    // 바텀시트 표시/숨김
    private void updateReservationSheetVisibility(boolean isLoggedIn, boolean hasActiveReservation) {
        if (!isLoggedIn || !hasActiveReservation) {
            bottomBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            bottomSheet.setVisibility(View.GONE);
            return;
        }
        if (bottomSheet.getVisibility() != View.VISIBLE) bottomSheet.setVisibility(View.VISIBLE);
        if (bottomBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            bottomBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    // 예약 정보 바인딩
    private void bindReservationDataToSheet(ReservationResponse r) {
        currentReservationId = r.id;

        String displayName = (r.routeName == null || r.routeName.isEmpty())
                ? r.routeId : r.routeName;
        if (tvRoute != null) tvRoute.setText(displayName);
        if (tvDir   != null) tvDir.setText(r.direction);
        if (tvFrom  != null) tvFrom.setText(r.boardStopName);
        if (tvTo    != null) tvTo.setText(r.destStopName);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> onClickCancel());
        }
    }

    private void initBottomSheetViews() {
        tvRoute  = bottomSheet.findViewById(R.id.tvBusNumber);
        tvDir    = bottomSheet.findViewById(R.id.tvBusDirection);
        tvFrom   = bottomSheet.findViewById(R.id.riging_station);
        tvTo     = bottomSheet.findViewById(R.id.out_station);
        btnCancel= bottomSheet.findViewById(R.id.btnReserve);
    }

    // 예약 취소
    private void onClickCancel() {
        String access = TokenStore.getAccess(getApplicationContext());
        if (TextUtils.isEmpty(access)) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentReservationId == null) {
            Toast.makeText(this, "취소할 예약이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiClient.get().cancelReservationById("Bearer " + access, currentReservationId)
                .enqueue(new retrofit2.Callback<CancelResult>() {
                    @Override public void onResponse(Call<CancelResult> call, Response<CancelResult> res) {
                        if (res.isSuccessful() && res.body()!=null) {
                            CancelResult cr = res.body();
                            switch (cr.result) {
                                case "CANCELLED" -> {
                                    Toast.makeText(MainActivity.this, "예약을 취소했어요.", Toast.LENGTH_SHORT).show();
                                    updateReservationSheetVisibility(true, false);
                                    currentReservationId = null;
                                    fetchAndShowActiveReservation();
                                }
                                case "ALREADY_CANCELLED" -> {
                                    Toast.makeText(MainActivity.this, "이미 취소된 예약이에요.", Toast.LENGTH_SHORT).show();
                                    updateReservationSheetVisibility(true, false);
                                    currentReservationId = null;
                                    fetchAndShowActiveReservation();
                                }
                                default -> Toast.makeText(MainActivity.this, "취소할 수 없는 상태입니다.", Toast.LENGTH_SHORT).show();
                            }
                        } else if (res.code()==401) {
                            TokenStore.clearAccess(getApplicationContext());
                            updateReservationSheetVisibility(false, false);
                            Toast.makeText(MainActivity.this, "로그인이 만료되었습니다.", Toast.LENGTH_SHORT).show();
                        } else if (res.code()==409) {
                            Toast.makeText(MainActivity.this, "취소할 예약이 없거나 취소할 수 없습니다.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "취소 실패 ("+res.code()+")", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onFailure(Call<CancelResult> call, Throwable t) {
                        Toast.makeText(MainActivity.this, "네트워크 오류로 취소 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 도착정보 시트 닫기
    private void dismissArrivalsSheetIfShown() {
        Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_ARRIVALS_SHEET);
        if (f instanceof androidx.fragment.app.DialogFragment df) {
            df.dismissAllowingStateLoss();
        }
    }

    private void enforceMainUiState() {
        if (hasActiveReservation) {
            dismissArrivalsSheetIfShown();
            updateReservationSheetVisibility(true, true);
        } else {
            updateReservationSheetVisibility(false, false);
        }
    }
}

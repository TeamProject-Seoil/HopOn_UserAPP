// app/src/main/java/com/example/testmap/activity/MainActivity.java
package com.example.testmap.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.content.res.ColorStateList;
import android.graphics.Color;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.adapter.FavoriteAdapter;
import com.example.testmap.adapter.RecentAdapter;
import com.example.testmap.dto.ReservationResponse;
import com.example.testmap.dto.RoutePoint;
import com.example.testmap.dto.StationDto;
import com.example.testmap.dto.ReservationCreateRequest;
import com.example.testmap.model.CancelResult;
import com.example.testmap.model.FavoriteItem;
import com.example.testmap.model.RecentItem;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.ui.ArrivalsBottomSheet;
import com.example.testmap.ui.ReserveCardDialogFragment;
import com.example.testmap.ui.LoginRequiredDialogFragment;
import com.example.testmap.util.TokenStore;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


/**
 * MainActivity
 * - 지도/주변정류장
 * - (서버 활성 예약이 있을 때만) 하단 바텀시트 노출
 * - 드로어: 메뉴 + 즐겨찾기/최근내역
 * - 드로어 아이템 클릭 시: 중앙 카드 다이얼로그(ReserveCardDialogFragment) 호출 → 즉시 예약 가능
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    // ===== Drawer + 지도 =====
    private DrawerLayout drawerLayout;
    private MapView mapView;
    private ImageButton menuButton, favoriteButton;
    private View layoutMenu, layoutFavorites;
    private NaverMap naverMap;

    private TextView badgeMenuNotice;      // 지도 화면 상단 메뉴 버튼 옆
    private TextView badgeRowNotice;

    // 위치 권한
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    //버스 경로 그리기
    private com.naver.maps.map.overlay.PathOverlay fullPathOverlay;
    private com.naver.maps.map.overlay.PathOverlay segmentPathOverlay;



    // 바텀시트 즐겨찾기 상태 동기화용
    @Nullable private ReservationResponse boundReservation = null;
    private boolean bottomSheetIsFav = false;
    @Nullable private Long bottomSheetFavId = null;

    // 위치 기준
    private static final int   RADIUS_M            = 1000;
    private static final float MIN_MOVE_METERS     = 25f;
    private static final long  MIN_INTERVAL_MS     = 7_000L;
    private static final float ACCURACY_MAX_METERS = 20f;

    // 위치 상태
    private LatLng lastFix = null;
    private LatLng lastFetchedCenter = null;
    private long   lastFetchAt = 0L;
    private boolean firstFixAccepted = false;

    // 정류장 마커
    private final List<Marker> stationMarkers = new ArrayList<>();

    // 메뉴 - 로그인/회원 UI
    private Button loginButton, registerButton;
    private View userPanel;
    private ImageView imageProfile;
    private TextView textUserName;
    private Button btnLogout;

    private static final String CLIENT_TYPE = "USER_APP";

    // (서버 활성 예약) 바텀시트
    private View bottomSheet;
    private BottomSheetBehavior<View> bottomBehavior;
    private TextView tvRoute, tvDir, tvFrom, tvTo;
    private View btnCancel;
    private Long currentReservationId = null;
    private static final String TAG_ARRIVALS_SHEET = "arrivals";
    private boolean hasActiveReservation = false;

    // ===== 즐겨찾기/최근 내역(드로어 안) =====
    private RecyclerView favRecycler, recentRecycler;
    private FavoriteAdapter favAdapter;
    private RecentAdapter recentAdapter;
    private final List<FavoriteItem> favItems = new ArrayList<>();
    private final List<Long> favIds = new ArrayList<>();
    private final Map<Long, ApiService.FavoriteResponse> favDetailById = new HashMap<>(); // ★ 상세 저장
    private final List<RecentItem> recentItems = new ArrayList<>();
    private TextView emptyFavText, emptyRecentText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        badgeMenuNotice = findViewById(R.id.menu_notice_badge);
        // initDrawerSections() 내, layoutMenu != null 블록 안 적당한 위치
        badgeRowNotice = (layoutMenu != null) ? layoutMenu.findViewById(R.id.row_notice_badge) : null;

        // (활성 예약) 바텀시트: 서버에 “현재 진행중 예약”이 있을 때만 표시
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
        drawerLayout   = findViewById(R.id.main);
        menuButton     = findViewById(R.id.menu_button);
        favoriteButton = findViewById(R.id.favorite_button);
        layoutMenu     = findViewById(R.id.activity_menu);
        layoutFavorites= findViewById(R.id.activity_favorites);

        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        // Drawer 버튼
        if (menuButton != null) {
            menuButton.setOnClickListener(view -> {
                refreshNoticeUnreadBadges(); // ← 추가
                if (layoutFavorites != null) layoutFavorites.setVisibility(View.GONE);
                if (layoutMenu != null)      layoutMenu.setVisibility(View.VISIBLE);
                if (drawerLayout != null)    drawerLayout.openDrawer(GravityCompat.START);
            });
        }
        if (favoriteButton != null) {
            favoriteButton.setOnClickListener(view -> {
                refreshNoticeUnreadBadges(); // ← 추가(옵션)
                String access = TokenStore.getAccess(MainActivity.this);
                if (TextUtils.isEmpty(access)) {
                    // 로그인 필요 다이얼로그
                    LoginRequiredDialogFragment.show(getSupportFragmentManager());
                    return;
                }
                if (layoutMenu != null)      layoutMenu.setVisibility(View.GONE);
                if (layoutFavorites != null) layoutFavorites.setVisibility(View.VISIBLE);
                if (drawerLayout != null)    drawerLayout.openDrawer(GravityCompat.START);

                // 표시 데이터 최신화
                fetchFavoritesIntoDrawer();
                fetchRecentsIntoDrawer();
            });
        }

        // 위치 권한(최초 요청)
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );

        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);

        // 드로어 섹션/리스트 초기화
        initDrawerSections();

        // 바텀시트 뷰 바인딩
        initBottomSheetViews();

        // 뒤로가기
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
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
                MainActivity.super.onBackPressed();
            }
        });

        enforceMainUiState();
    }

    // ===== 생명주기 =====
    @Override protected void onStart()  { super.onStart();  mapView.onStart(); }
    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        renderHeaderByAuth();
        ensureLocationTracking();
        fetchAndShowActiveReservation();
        refreshNoticeUnreadBadges();
    }
    @Override protected void onPause()  { super.onPause();  mapView.onPause(); }
    @Override protected void onStop()   { super.onStop();   mapView.onStop(); }
    @Override protected void onDestroy(){ super.onDestroy();mapView.onDestroy(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }

    // ===== 권한 결과 =====
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            ensureLocationTracking();
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // ===== 지도 준비 =====
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

    // ===== 위치 관련 =====
    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void promptTurnOnLocationIfNeeded() {
        Toast.makeText(this, "기기 위치가 꺼져 있어요. 켜야 현재 위치를 표시할 수 있어요.", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

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

    // 주변 정류장
    private void fetchNearStations(double longitude, double latitude, int radius) {
        ApiClient.get().getNearbyStations(longitude, latitude, radius)
                .enqueue(new retrofit2.Callback<List<StationDto>>() {
                    @Override public void onResponse(retrofit2.Call<List<StationDto>> call,
                                                     retrofit2.Response<List<StationDto>> res) {
                        if (!res.isSuccessful() || res.body() == null) {
                            android.util.Log.e("API", "nearstations HTTP " + res.code());
                            return;
                        }
                        renderStationMarkers(res.body());
                    }
                    @Override public void onFailure(retrofit2.Call<List<StationDto>> call, Throwable t) {
                        android.util.Log.e("API", "nearstations failed", t);
                    }
                });
    }

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

            m.setTag(s);
            m.setOnClickListener(overlay -> {
                if (hasActiveReservation) return true;
                StationDto st = (StationDto) overlay.getTag();
                ArrivalsBottomSheet f = ArrivalsBottomSheet.newInstance(
                        st.arsId,
                        st.stationName
                );
                f.show(getSupportFragmentManager(), TAG_ARRIVALS_SHEET);
                return true;
            });
        }
    }

    private static float distanceMeters(LatLng a, LatLng b) {
        if (a == null || b == null) return Float.MAX_VALUE;
        float[] out = new float[1];
        android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out);
        return out[0];
    }

    // ===== 인증 상태 =====
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

        // ★ 확인 다이얼로그로 교체
        if (btnLogout != null) btnLogout.setOnClickListener(v -> confirmLogout());

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

        onLoggedOutCleanup();
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

    /** 머티리얼 커스텀 로그아웃 확인(리소스가 없으면 기본 다이얼로그로 폴백) */
    private void confirmLogout() {
        // 1) 커스텀 레이아웃 동적 탐색
        int layoutId = getResources().getIdentifier("dialog_confirm_logout", "layout", getPackageName());
        if (layoutId != 0) {
            View content = getLayoutInflater().inflate(layoutId, null, false);
            AlertDialog dialog = new MaterialAlertDialogBuilder(MainActivity.this)
                    .setView(content)
                    .create();

            // 배경(옵션): bg_white_card가 있으면 적용, 없으면 투명
            int bgId = getResources().getIdentifier("bg_white_card", "drawable", getPackageName());
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }

            Button btnCancel = content.findViewById(R.id.btnCancel);
            Button btnLogout = content.findViewById(R.id.btnLogout);
            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnLogout.setOnClickListener(v -> {
                dialog.dismiss();
                doLogout(); // 기존 로그아웃 로직 호출
            });

            dialog.show();
            return;
        }

        // 2) 폴백: 기본 머티리얼 다이얼로그
        new MaterialAlertDialogBuilder(MainActivity.this)
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃 하시겠어요?")
                .setNegativeButton("취소", (d, w) -> d.dismiss())
                .setPositiveButton("로그아웃", (d, w) -> {
                    d.dismiss();
                    doLogout();
                })
                .show();
    }

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

        clearPathOverlays();
    }

    // ===== 예약(활성 여부 UI만 바텀시트) =====
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
                    if (bottomBehavior != null) bottomBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
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

    private void updateReservationSheetVisibility(boolean isLoggedIn, boolean hasActiveReservation) {
        if (bottomSheet == null || bottomBehavior == null) return;

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
        wireFavoriteInBottomSheet(r);
        fetchAndDrawPolylinesForReservation(r);
        boundReservation = r;
    }

    // ===== 즐겨찾기: 고정 바텀시트 토글 =====
    private void wireFavoriteInBottomSheet(ReservationResponse r) {
        if (bottomSheet == null) return;
        ImageView star = bottomSheet.findViewById(R.id.btnFavoriteActive);
        if (star == null) return;

        // 현재 상태 계산 후 필드에 저장
        Long matchedId = findFavoriteIdFor(r);
        bottomSheetIsFav = matchedId != null;
        bottomSheetFavId = matchedId;
        applyStarTint(star, bottomSheetIsFav);

        star.setOnClickListener(v -> {
            String access = TokenStore.getAccess(getApplicationContext());
            if (TextUtils.isEmpty(access)) {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
                LoginRequiredDialogFragment.show(getSupportFragmentManager());
                return;
            }
            String bearer = "Bearer " + access;

            if (!bottomSheetIsFav) {
                // 추가
                ApiService.FavoriteCreateRequest body = new ApiService.FavoriteCreateRequest(
                        r.routeId, r.direction,
                        r.boardStopId, r.boardStopName, r.boardArsId,
                        r.destStopId,  r.destStopName,  r.destArsId,
                        r.routeName
                );
                ApiClient.get().addFavorite(bearer, body)
                        .enqueue(new retrofit2.Callback<ApiService.FavoriteResponse>() {
                            @Override public void onResponse(retrofit2.Call<ApiService.FavoriteResponse> call,
                                                             retrofit2.Response<ApiService.FavoriteResponse> res) {
                                if (res.isSuccessful() && res.body()!=null) {
                                    bottomSheetIsFav = true;
                                    bottomSheetFavId = res.body().id;
                                    favDetailById.put(res.body().id, res.body());
                                    fetchFavoritesIntoDrawer(); // 드로어+별 동기화
                                    applyStarTint(star, true);
                                    Toast.makeText(MainActivity.this, "즐겨찾기에 추가되었습니다.", Toast.LENGTH_SHORT).show();
                                } else if (res.code()==409) {
                                    fetchFavoritesIntoDrawer();
                                    bottomSheetIsFav = true;
                                    applyStarTint(star, true);
                                    Toast.makeText(MainActivity.this, "이미 즐겨찾기에 있습니다.", Toast.LENGTH_SHORT).show();
                                } else if (res.code()==401) {
                                    TokenStore.clearAccess(getApplicationContext());
                                    LoginRequiredDialogFragment.show(getSupportFragmentManager());
                                } else {
                                    Toast.makeText(MainActivity.this, "추가 실패 ("+res.code()+")", Toast.LENGTH_SHORT).show();
                                }
                            }
                            @Override public void onFailure(retrofit2.Call<ApiService.FavoriteResponse> call, Throwable t) {
                                Toast.makeText(MainActivity.this, "네트워크 오류로 추가 실패", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                // 삭제
                Long idToDelete = bottomSheetFavId;
                if (idToDelete == null) {
                    resolveFavoriteIdThen(bearer, r.routeId, r.direction, r.boardStopId, r.destStopId, id -> {
                        if (id == null) {
                            Toast.makeText(MainActivity.this, "삭제할 즐겨찾기를 찾지 못했습니다.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        doDeleteFavorite(bearer, id, star, new boolean[]{bottomSheetIsFav}, new Long[]{bottomSheetFavId});
                        // 삭제 성공 시 필드도 갱신
                        bottomSheetIsFav = false;
                        bottomSheetFavId = null;
                        applyStarTint(star, false);
                    });
                } else {
                    ApiClient.get().deleteFavorite(bearer, idToDelete)
                            .enqueue(new retrofit2.Callback<Void>() {
                                @Override public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> res) {
                                    if (res.isSuccessful() || res.code()==404) {
                                        bottomSheetIsFav = false;
                                        bottomSheetFavId = null;
                                        favDetailById.remove(idToDelete);
                                        fetchFavoritesIntoDrawer();
                                        applyStarTint(star, false);
                                        Toast.makeText(MainActivity.this, "즐겨찾기에서 제거되었습니다.", Toast.LENGTH_SHORT).show();
                                    } else if (res.code()==401) {
                                        TokenStore.clearAccess(getApplicationContext());
                                        LoginRequiredDialogFragment.show(getSupportFragmentManager());
                                    } else {
                                        Toast.makeText(MainActivity.this, "삭제 실패 ("+res.code()+")", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                                    Toast.makeText(MainActivity.this, "네트워크 오류로 삭제 실패", Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            }
        });
    }


    private void doDeleteFavorite(String bearer, long id, ImageView star, boolean[] isFav, Long[] favIdHolder) {
        ApiClient.get().deleteFavorite(bearer, id)
                .enqueue(new retrofit2.Callback<Void>() {
                    @Override public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> res) {
                        if (res.isSuccessful() || res.code()==404) {
                            isFav[0] = false;
                            favIdHolder[0] = null;
                            favDetailById.remove(id);
                            fetchFavoritesIntoDrawer();
                            applyStarTint(star, false);
                            Toast.makeText(MainActivity.this, "즐겨찾기에서 제거되었습니다.", Toast.LENGTH_SHORT).show();

                        } else if (res.code()==401) {
                            TokenStore.clearAccess(getApplicationContext());
                            LoginRequiredDialogFragment.show(getSupportFragmentManager());

                        } else {
                            Toast.makeText(MainActivity.this, "삭제 실패 ("+res.code()+")", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                        Toast.makeText(MainActivity.this, "네트워크 오류로 삭제 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    private Long findFavoriteIdFor(ReservationResponse r) {
        for (Map.Entry<Long, ApiService.FavoriteResponse> e : favDetailById.entrySet()) {
            ApiService.FavoriteResponse f = e.getValue();
            boolean same =
                    TextUtils.equals(f.routeId, r.routeId) &&
                            TextUtils.equals(nullToEmpty(f.direction), nullToEmpty(r.direction)) &&
                            TextUtils.equals(f.boardStopId, r.boardStopId) &&
                            TextUtils.equals(f.destStopId, r.destStopId);
            if (same) return e.getKey();
        }
        return null;
    }

    private interface IdCb { void onResolved(@Nullable Long id); }
    private void resolveFavoriteIdThen(String bearer,
                                       String routeId, @Nullable String direction,
                                       String boardStopId, String destStopId,
                                       IdCb cb) {
        ApiClient.get().getFavorites(bearer)
                .enqueue(new retrofit2.Callback<List<ApiService.FavoriteResponse>>() {
                    @Override public void onResponse(retrofit2.Call<List<ApiService.FavoriteResponse>> call,
                                                     retrofit2.Response<List<ApiService.FavoriteResponse>> res) {
                        if (!res.isSuccessful() || res.body()==null) { cb.onResolved(null); return; }
                        for (ApiService.FavoriteResponse f : res.body()) {
                            boolean same =
                                    TextUtils.equals(f.routeId, routeId) &&
                                            TextUtils.equals(nullToEmpty(f.direction), nullToEmpty(direction)) &&
                                            TextUtils.equals(f.boardStopId, boardStopId) &&
                                            TextUtils.equals(f.destStopId, destStopId);
                            if (same) { cb.onResolved(f.id); return; }
                        }
                        cb.onResolved(null);
                    }
                    @Override public void onFailure(retrofit2.Call<List<ApiService.FavoriteResponse>> call, Throwable t) {
                        cb.onResolved(null);
                    }
                });
    }

    private static String nullToEmpty(@Nullable String s) { return s == null ? "" : s; }

    private void applyStarTint(ImageView star, boolean fav) {
        if (star == null) return;
        int color = fav ? Color.parseColor("#FFC107") : Color.parseColor("#BDBDBD");
        ImageViewCompat.setImageTintList(star, ColorStateList.valueOf(color));
        star.setContentDescription(fav ? "즐겨찾기 제거" : "즐겨찾기 추가");
        star.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100)
                .withEndAction(() -> star.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                .start();
    }

    private void syncBottomSheetFavoriteStateIfNeeded() {
        if (bottomSheet == null || boundReservation == null) return;
        ImageView star = bottomSheet.findViewById(R.id.btnFavoriteActive);
        if (star == null) return;

        Long matched = findFavoriteIdFor(boundReservation);
        bottomSheetIsFav = matched != null;
        bottomSheetFavId = matched;
        applyStarTint(star, bottomSheetIsFav);
    }

    private void initBottomSheetViews() {
        if (bottomSheet == null) return;
        tvRoute  = bottomSheet.findViewById(R.id.tvBusNumber);
        tvDir    = bottomSheet.findViewById(R.id.tvBusDirection);
        tvFrom   = bottomSheet.findViewById(R.id.riging_station);
        tvTo     = bottomSheet.findViewById(R.id.out_station);
        btnCancel= bottomSheet.findViewById(R.id.btnReserve); // 시트 UI 구성에 맞게 사용
    }

    private void onClickCancel() {
        String access = TokenStore.getAccess(getApplicationContext());
        if (TextUtils.isEmpty(access)) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            LoginRequiredDialogFragment.show(getSupportFragmentManager());
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
                                    clearPathOverlays();
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
                            LoginRequiredDialogFragment.show(getSupportFragmentManager());
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

    // ===== Drawer 섹션 초기화 및 즐겨찾기/최근 내역 UI =====
    private void initDrawerSections() {
        if (layoutMenu != null)      layoutMenu.setVisibility(View.VISIBLE);
        if (layoutFavorites != null) layoutFavorites.setVisibility(View.GONE);

        // 메뉴 섹션 뒤로가기
        if (layoutMenu != null) {
            ImageView backIconMenu = layoutMenu.findViewById(R.id.back_icon);
            if (backIconMenu != null) backIconMenu.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            });

            badgeRowNotice = layoutMenu.findViewById(R.id.row_notice_badge);
        }

        // 즐겨찾기 섹션
        if (layoutFavorites != null) {
            ImageView backIconFav = layoutFavorites.findViewById(R.id.back_icon);
            if (backIconFav != null) backIconFav.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            });


            // ✅ 여기로 이동: 상단바 '전체 삭제' 버튼 연결
            View clearAll = layoutFavorites.findViewById(R.id.clearAll);
            if (clearAll != null) {
                clearAll.setOnClickListener(v -> {
                    String access = TokenStore.getAccess(MainActivity.this);
                    if (TextUtils.isEmpty(access)) {
                        Toast.makeText(MainActivity.this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
                        LoginRequiredDialogFragment.show(getSupportFragmentManager());
                        return;
                    }
                    if (favIds.isEmpty()) {
                        Toast.makeText(MainActivity.this, "삭제할 즐겨찾기가 없습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 커스텀 뷰 inflate
                    View content = getLayoutInflater().inflate(R.layout.dialog_confirm_delete_all, null, false);
                    AlertDialog dialog = new MaterialAlertDialogBuilder(MainActivity.this)
                            .setView(content)
                            .create();

                    // 배경 흰색+라운드 적용
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawable(
                                ContextCompat.getDrawable(MainActivity.this, R.drawable.bg_white_card)
                        );
                    }

                    // 버튼 핸들러
                    Button btnCancel = content.findViewById(R.id.btnCancel);
                    Button btnDelete = content.findViewById(R.id.btnDelete);
                    btnCancel.setOnClickListener(dv -> dialog.dismiss());
                    btnDelete.setOnClickListener(dv -> {
                        dialog.dismiss();
                        clearAllFavorites();
                    });

                    dialog.show();
                });
            }


            favRecycler     = layoutFavorites.findViewById(R.id.fav_recycler);
            recentRecycler  = layoutFavorites.findViewById(R.id.recent_recycler);
            emptyFavText    = layoutFavorites.findViewById(R.id.emptyFavText);
            emptyRecentText = layoutFavorites.findViewById(R.id.emptyRecentText);

            if (favRecycler != null) {
                favRecycler.setLayoutManager(new LinearLayoutManager(this));
                favAdapter = new FavoriteAdapter();
                favRecycler.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
                favRecycler.setAdapter(favAdapter);
                favRecycler.getLayoutParams().height = dpToPx(4 * 72);
                favRecycler.setNestedScrollingEnabled(true);

                // 클릭 리스너: 아이템 탭 → 중앙 카드 다이얼로그 → 예약
                favAdapter.setOnFavoriteClickListener(new FavoriteAdapter.OnFavoriteClickListener() {
                    @Override public void onClickItem(FavoriteItem item, int position) {
                        onClickFavoriteItem(position, item);
                    }
                    @Override public void onClickUnstar(FavoriteItem item, int position) {
                        if (position < 0 || position >= favIds.size()) return;

                        Long id = favIds.get(position);
                        String access = TokenStore.getAccess(MainActivity.this);
                        if (TextUtils.isEmpty(access)) {
                            LoginRequiredDialogFragment.show(getSupportFragmentManager());
                            return;
                        }

                        // 서버 삭제(성공/실패와 무관하게 끝나면 재조회)
                        ApiClient.get().deleteFavorite("Bearer " + access, id)
                                .enqueue(new retrofit2.Callback<Void>() {
                                    @Override public void onResponse(Call<Void> call, Response<Void> res) {
                                        fetchFavoritesIntoDrawer();
                                    }
                                    @Override public void onFailure (Call<Void> call, Throwable t) {
                                        fetchFavoritesIntoDrawer();
                                    }
                                });

                        // ✅ 낙관적 UI 업데이트: 수동 애니메이션 X, 정확 포지션 제거
                        //    (어댑터 내부 리스트와 외부 리스트 둘 다 제거해서 동기 유지)
                        if (position < favItems.size()) favItems.remove(position);
                        if (position < favIds.size())   favIds.remove(position);

                        if (favAdapter != null) {
                            favAdapter.removeAt(position); // notifyItemRemoved(position)
                            // 뒤 인덱스가 포지션 의존이면 범위 갱신
                            // favAdapter.notifyItemRangeChanged(position, favAdapter.getItemCount() - position);
                        }

                        updateDrawerEmpty();
                    }
                });
            }

            if (recentRecycler != null) {
                recentRecycler.setLayoutManager(new LinearLayoutManager(this));
                recentAdapter = new RecentAdapter(recentItems);
                recentRecycler.setAdapter(recentAdapter);
                recentRecycler.getLayoutParams().height = dpToPx(4 * 72);
                recentRecycler.setNestedScrollingEnabled(true);

                // 최근 아이템 클릭 → 중앙 카드 다이얼로그 → 예약
                recentAdapter.setOnItemClickListener(new RecentAdapter.OnItemClickListener() {
                    @Override public void onItemClick(RecentItem item) {
                        onClickRecentItem(item);
                    }
                    @Override public void onAddFavClick(RecentItem item) { addFavoriteFromRecentInDrawer(item); }
                });
            }
        }

        // 공통: 로그인/메뉴 항목
        loginButton   = findViewById(R.id.login_button);
        registerButton= findViewById(R.id.register_button);
        if (loginButton != null)
            loginButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LoginActivity.class)));
        if (registerButton != null)
            registerButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, RegisterActivity.class)));

        userPanel    = findViewById(R.id.user_panel);
        imageProfile = findViewById(R.id.image_profile);
        textUserName = findViewById(R.id.text_user_name);
        btnLogout    = findViewById(R.id.btn_logout);
        // ★ 확인 다이얼로그로 교체
        if (btnLogout != null) btnLogout.setOnClickListener(v -> confirmLogout());

        View menuSection = findViewById(R.id.menu_section);
        if (menuSection != null) {
            View rowSettings = menuSection.findViewById(R.id.row_settings);
            if (rowSettings != null)
                rowSettings.setOnClickListener(v -> {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                });

            View rowNotice = menuSection.findViewById(R.id.row_notice);
            if (rowNotice != null)
                rowNotice.setOnClickListener(v -> {
                    startActivity(new Intent(MainActivity.this, NoticeActivity.class));
                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                });

            View rowOpensource = menuSection.findViewById(R.id.row_opensource);
            if (rowOpensource != null)
                rowOpensource.setOnClickListener(v -> {
                    Toast.makeText(MainActivity.this, "오픈소스 활용정보는 준비 중입니다.", Toast.LENGTH_SHORT).show();
                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                });
        }
    }

    private void refreshNoticeUnreadBadges() {
        // 로그인 안돼있으면 둘 다 숨김
        String access = TokenStore.getAccess(getApplicationContext());
        if (TextUtils.isEmpty(access)) {
            setBadgeCount(badgeMenuNotice, 0);
            setBadgeCount(badgeRowNotice, 0);
            return;
        }
        String bearer = "Bearer " + access;

        // 서버에서 안읽음 개수 조회
        ApiClient.get().getNoticeUnreadCount(bearer)
                .enqueue(new retrofit2.Callback<ApiService.UnreadCountResp>() {
                    @Override public void onResponse(retrofit2.Call<ApiService.UnreadCountResp> call,
                                                     retrofit2.Response<ApiService.UnreadCountResp> res) {
                        long c = 0L;
                        if (res.isSuccessful() && res.body()!=null) c = Math.max(0L, res.body().count);
                        applyNoticeBadgeCount(c);
                    }
                    @Override public void onFailure(retrofit2.Call<ApiService.UnreadCountResp> call, Throwable t) {
                        // 실패 시 숨김(또는 직전값 유지하고 싶으면 no-op)
                        applyNoticeBadgeCount(0L);
                    }
                });
    }

    private void applyNoticeBadgeCount(long count) {
        setBadgeCount(badgeMenuNotice, count);
        setBadgeCount(badgeRowNotice, count);
    }

    // 공통: 0이면 GONE, 있으면 표시(99+ 처리)
    private void setBadgeCount(@Nullable TextView badge, long count) {
        if (badge == null) return;
        if (count <= 0) {
            badge.setVisibility(View.GONE);
        } else {
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
            if (badge.getVisibility() != View.VISIBLE) {
                badge.setScaleX(0.8f);
                badge.setScaleY(0.8f);
                badge.setAlpha(0f);
                badge.setVisibility(View.VISIBLE);
                badge.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start();
            }
        }
    }

    private void clearAllFavorites() {
        String access = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(access)) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            LoginRequiredDialogFragment.show(getSupportFragmentManager());
            return;
        }
        final String bearer = "Bearer " + access;

        // 현재 메모리의 즐겨찾기 ID 스냅샷
        List<Long> ids = new ArrayList<>(favIds);
        if (ids.isEmpty()) {
            Toast.makeText(this, "삭제할 즐겨찾기가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        final int total = ids.size();
        final int[] done = {0};
        final int[] ok   = {0};

        for (Long id : ids) {
            ApiClient.get().deleteFavorite(bearer, id)
                    .enqueue(new retrofit2.Callback<Void>() {
                        @Override public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> res) {
                            // 성공이든 404(이미 없음)이든 진행 카운트만 올림
                            if (res.isSuccessful() || res.code() == 404) ok[0]++;
                            if (++done[0] == total) {
                                // 모두 끝나면 서버 상태 재조회 + 토스트
                                fetchFavoritesIntoDrawer();
                                Toast.makeText(MainActivity.this, "즐겨찾기 전체 삭제 완료 ("+ ok[0] +"/"+ total +")", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                            if (++done[0] == total) {
                                fetchFavoritesIntoDrawer();
                                Toast.makeText(MainActivity.this, "일부 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }

        // 낙관적 UI: 즉시 비우고 상태 반영(네트워크 완료 후 fetch로 최종 동기화)
        favItems.clear();
        favIds.clear();
        favDetailById.clear();
        if (favAdapter != null) favAdapter.setItems(favItems);
        updateDrawerEmpty();
    }

    // ===== 드로어 데이터 로딩 =====
    private void fetchFavoritesIntoDrawer() {
        String access = TokenStore.getAccess(this);
        favItems.clear();
        favIds.clear();
        favDetailById.clear();

        if (TextUtils.isEmpty(access)) {
            if (favAdapter != null) favAdapter.setItems(favItems);
            updateDrawerEmpty();
            return;
        }

        ApiClient.get().getFavorites("Bearer " + access)
                .enqueue(new retrofit2.Callback<List<ApiService.FavoriteResponse>>() {
                    @Override
                    public void onResponse(Call<List<ApiService.FavoriteResponse>> call, Response<List<ApiService.FavoriteResponse>> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            List<ApiService.FavoriteResponse> list = res.body();
                            for (ApiService.FavoriteResponse f : list) {
                                String title = (f.routeName != null && !f.routeName.isEmpty()) ? f.routeName : f.routeId;
                                String sub   = safeJoin(f.boardStopName, " → ", f.destStopName);
                                String dir   = TextUtils.isEmpty(f.direction) ? "방면 정보 없음" : f.direction;

                                favItems.add(new FavoriteItem(title, sub, dir));
                                favIds.add(f.id);
                                favDetailById.put(f.id, f); // ★ 상세 보관(예약용)
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "즐겨찾기 불러오기 실패 ("+res.code()+")", Toast.LENGTH_SHORT).show();
                        }
                        if (favAdapter != null) favAdapter.setItems(favItems);
                        updateDrawerEmpty();
                        syncBottomSheetFavoriteStateIfNeeded();
                    }

                    @Override
                    public void onFailure(Call<List<ApiService.FavoriteResponse>> call, Throwable t) {
                        Toast.makeText(MainActivity.this, "즐겨찾기 불러오기 실패", Toast.LENGTH_SHORT).show();
                        if (favAdapter != null) favAdapter.setItems(favItems);
                        updateDrawerEmpty();
                        syncBottomSheetFavoriteStateIfNeeded();
                    }
                });
    }

    private void fetchRecentsIntoDrawer() {
        String access = TokenStore.getAccess(this);
        recentItems.clear();

        if (TextUtils.isEmpty(access)) {
            if (recentAdapter != null) recentAdapter.notifyDataSetChanged();
            updateDrawerEmpty();
            return;
        }

        ApiClient.get().getReservations("Bearer " + access)
                .enqueue(new retrofit2.Callback<List<ReservationResponse>>() {
                    @Override
                    public void onResponse(Call<List<ReservationResponse>> call, Response<List<ReservationResponse>> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            List<ReservationResponse> list = res.body();
                            int limit = Math.min(20, list.size());
                            for (int i = 0; i < limit; i++) {
                                recentItems.add(new RecentItem(list.get(i)));
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "최근 내역 불러오기 실패 ("+res.code()+")", Toast.LENGTH_SHORT).show();
                        }
                        if (recentAdapter != null) recentAdapter.notifyDataSetChanged();
                        updateDrawerEmpty();
                    }

                    @Override
                    public void onFailure(Call<List<ReservationResponse>> call, Throwable t) {
                        Toast.makeText(MainActivity.this, "최근 내역 불러오기 실패", Toast.LENGTH_SHORT).show();
                        if (recentAdapter != null) recentAdapter.notifyDataSetChanged();
                        updateDrawerEmpty();
                    }
                });
    }

    private void addFavoriteFromRecentInDrawer(RecentItem item) {
        String access = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(access)) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            LoginRequiredDialogFragment.show(getSupportFragmentManager());
            return;
        }

        ApiService.FavoriteCreateRequest body = new ApiService.FavoriteCreateRequest(
                item.getRouteId(),
                item.getDirection(),
                item.getBoardStopId(),
                item.getBoardStopName(),
                item.getBoardArsId(),
                item.getDestStopId(),
                item.getDestStopName(),
                item.getDestArsId(),
                item.getRouteName()
        );

        ApiClient.get().addFavorite("Bearer " + access, body)
                .enqueue(new retrofit2.Callback<ApiService.FavoriteResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.FavoriteResponse> call,
                                           Response<ApiService.FavoriteResponse> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            // 추가 성공 → 즉시 서버 상태 재조회
                            fetchFavoritesIntoDrawer();
                            Toast.makeText(MainActivity.this, "즐겨찾기에 추가되었습니다.", Toast.LENGTH_SHORT).show();

                        } else if (res.code() == 409) {
                            // 이미 있음 → 동기화(혹시 다른 기기에서 변경됐을 수 있음)
                            fetchFavoritesIntoDrawer();
                            Toast.makeText(MainActivity.this, "이미 즐겨찾기에 있습니다.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "추가 실패 (" + res.code() + ")", Toast.LENGTH_SHORT).show();
                        }

                    }

                    @Override
                    public void onFailure(Call<ApiService.FavoriteResponse> call, Throwable t) {
                        Toast.makeText(MainActivity.this, "네트워크 오류로 추가 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ====== 드로어 아이템 클릭 → 중앙 카드 띄우기 & 예약 ======

    private void onClickFavoriteItem(int position, FavoriteItem item) {
        if (position < 0 || position >= favIds.size()) return;
        Long id = favIds.get(position);
        ApiService.FavoriteResponse f = favDetailById.get(id);
        if (f == null) {
            Toast.makeText(this, "즐겨찾기 상세를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String busNo = !TextUtils.isEmpty(f.routeName) ? f.routeName : f.routeId;
        String dir   = TextUtils.isEmpty(f.direction) ? "" : f.direction;
        String from  = TextUtils.isEmpty(f.boardStopName) ? "" : f.boardStopName;
        String to    = TextUtils.isEmpty(f.destStopName)  ? "" : f.destStopName;

        // ★ 서버 연동 가능한 전체 파라미터로 다이얼로그 생성
        ReserveCardDialogFragment dialog = ReserveCardDialogFragment.newInstanceFull(
                /* 표시용 */ busNo, dir, from, to,
                /* 서버용 */ f.routeId, f.direction,
                f.boardStopId, f.boardStopName, f.boardArsId,
                f.destStopId,  f.destStopName,  f.destArsId,
                f.routeName,
                /* 초기 즐겨찾기 상태 */ true, f.id
        );

        dialog.setOnActionListener(new ReserveCardDialogFragment.OnActionListener() {
            @Override public void onReserveClicked(boolean boardingAlarm, boolean dropOffAlarm) {
                dialog.dismissAllowingStateLoss();
                createReservationFromFavorite(f);
            }
            @Override public void onCancelClicked() { /* no-op */ }

            // ★ 다이얼로그에서 별 토글 시 → 서버 기준으로 즉시 재조회
            @Override public void onFavoriteChanged(boolean isFav, Long favId) {
                fetchFavoritesIntoDrawer();
            }
        });
        dialog.show(getSupportFragmentManager(), "reserve_card");
    }


    private void onClickRecentItem(RecentItem item) {
        String busNo = !TextUtils.isEmpty(item.getRouteName()) ? item.getRouteName() : item.getRouteId();
        String dir   = item.getDirection()   == null ? "" : item.getDirection();
        String from  = item.getBoardStopName()== null ? "" : item.getBoardStopName();
        String to    = item.getDestStopName() == null ? "" : item.getDestStopName();

        // ★ 현재 보유한 즐겨찾기에서 동일 조합 찾기 (routeId+direction+boardStopId+destStopId)
        Long matchedFavId = null;
        for (Map.Entry<Long, ApiService.FavoriteResponse> e : favDetailById.entrySet()) {
            ApiService.FavoriteResponse f = e.getValue();
            if (TextUtils.equals(f.routeId, item.getRouteId())
                    && TextUtils.equals(empty(f.direction), empty(item.getDirection()))
                    && TextUtils.equals(f.boardStopId, item.getBoardStopId())
                    && TextUtils.equals(f.destStopId, item.getDestStopId())) {
                matchedFavId = e.getKey();
                break;
            }
        }
        final Long matchedFavIdFinal = matchedFavId; // ← 콜백에서 사용할 final 변수
        boolean isFavorite = matchedFavIdFinal != null;

        ReserveCardDialogFragment dialog = ReserveCardDialogFragment.newInstanceFull(
                /* 표시용 */ busNo, dir, from, to,
                /* 서버용 */ item.getRouteId(), item.getDirection(),
                item.getBoardStopId(), item.getBoardStopName(), item.getBoardArsId(),
                item.getDestStopId(),  item.getDestStopName(),  item.getDestArsId(),
                item.getRouteName(),
                /* 초기 즐겨찾기 상태 */ isFavorite, matchedFavIdFinal
        );

        dialog.setOnActionListener(new ReserveCardDialogFragment.OnActionListener() {
            @Override public void onReserveClicked(boolean boardingAlarm, boolean dropOffAlarm) {
                dialog.dismissAllowingStateLoss();
                createReservationFromRecent(item);
            }
            @Override public void onCancelClicked() { /* no-op */ }

            // ★ 토글 결과는 서버 기준으로 즉시 동기화
            @Override public void onFavoriteChanged(boolean nowFav, Long favId) {
                fetchFavoritesIntoDrawer();
            }
        });
        dialog.show(getSupportFragmentManager(), "reserve_card");
    }


    private static String empty(String s){ return s==null? "": s; }

    /** 로그아웃 후 UI/메모리 싹 정리 */
    private void onLoggedOutCleanup() {
        // 공지 배지 숨김
        applyNoticeBadgeCount(0L);

        // 바텀시트/예약 상태 초기화
        boundReservation = null;
        currentReservationId = null;
        hasActiveReservation = false;
        bottomSheetIsFav = false;
        bottomSheetFavId = null;
        updateReservationSheetVisibility(false, false);
        dismissArrivalsSheetIfShown();

        // 즐겨찾기/최근내역 메모리 & 화면 비우기
        favItems.clear();
        favIds.clear();
        favDetailById.clear();
        if (favAdapter != null) favAdapter.setItems(favItems);

        recentItems.clear();
        if (recentAdapter != null) recentAdapter.notifyDataSetChanged();

        updateDrawerEmpty();
    }

    private void showReserveCard(String busNo, String dir, String from, String to, Runnable onReserve) {
        // 중앙 카드(다이얼로그) 띄우기
        ReserveCardDialogFragment f = ReserveCardDialogFragment.newInstance(busNo, dir, from, to);
        f.setOnActionListener(new ReserveCardDialogFragment.OnActionListener() {
            @Override public void onReserveClicked(boolean boardingAlarm, boolean dropOffAlarm) {
                // 알림 체크박스 값이 필요하면 onReserve 안에서 함께 전달받아 쓰면 됨
                if (onReserve != null) onReserve.run();
            }
            @Override public void onCancelClicked() { /* no-op */ }
        });
        f.show(getSupportFragmentManager(), "reserve_card");
    }

    private void createReservationFromFavorite(ApiService.FavoriteResponse f) {
        String access = TokenStore.getAccess(getApplicationContext());
        if (TextUtils.isEmpty(access)) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            LoginRequiredDialogFragment.show(getSupportFragmentManager());
            return;
        }

        ReservationCreateRequest req = new ReservationCreateRequest();
        req.routeId       = f.routeId;
        req.direction     = f.direction;
        req.boardStopId   = f.boardStopId;
        req.boardStopName = f.boardStopName;
        req.boardArsId    = f.boardArsId;
        req.destStopId    = f.destStopId;
        req.destStopName  = f.destStopName;
        req.destArsId     = f.destArsId;
        req.routeName     = f.routeName;

        ApiClient.get().createReservation("Bearer " + access, req).enqueue(new Callback<ReservationResponse>() {
            @Override public void onResponse(Call<ReservationResponse> call, Response<ReservationResponse> resp) {
                if (resp.isSuccessful() && resp.body()!=null) {
                    getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("JUST_RESERVED", true).apply();
                    fetchAndShowActiveReservation();
                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    if (resp.code()==401) {
                        TokenStore.clearAccess(getApplicationContext());
                        Toast.makeText(MainActivity.this, "로그인이 만료되었습니다.", Toast.LENGTH_SHORT).show();
                        LoginRequiredDialogFragment.show(getSupportFragmentManager());
                        return;
                    }
                    String msg = (resp.code()==409) ? "예약 불가(중복/정책 위반)" :
                            (resp.code()==422) ? "진행방향이 맞지 않습니다." :
                                    "예약 실패: HTTP "+resp.code();
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ReservationResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "네트워크 오류: "+t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createReservationFromRecent(RecentItem item) {
        String access = TokenStore.getAccess(getApplicationContext());
        if (TextUtils.isEmpty(access)) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            LoginRequiredDialogFragment.show(getSupportFragmentManager());
            return;
        }

        ReservationCreateRequest req = new ReservationCreateRequest();
        req.routeId       = item.getRouteId();
        req.direction     = item.getDirection();
        req.boardStopId   = item.getBoardStopId();
        req.boardStopName = item.getBoardStopName();
        req.boardArsId    = item.getBoardArsId();
        req.destStopId    = item.getDestStopId();
        req.destStopName  = item.getDestStopName();
        req.destArsId     = item.getDestArsId();
        req.routeName     = item.getRouteName();

        ApiClient.get().createReservation("Bearer " + access, req).enqueue(new Callback<ReservationResponse>() {
            @Override public void onResponse(Call<ReservationResponse> call, Response<ReservationResponse> resp) {
                if (resp.isSuccessful() && resp.body()!=null) {
                    getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("JUST_RESERVED", true).apply();
                    fetchAndShowActiveReservation();
                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    if (resp.code()==401) {
                        TokenStore.clearAccess(getApplicationContext());
                        Toast.makeText(MainActivity.this, "로그인이 만료되었습니다.", Toast.LENGTH_SHORT).show();
                        LoginRequiredDialogFragment.show(getSupportFragmentManager());
                        return;
                    }
                    String msg = (resp.code()==409) ? "예약 불가(중복/정책 위반)" :
                            (resp.code()==422) ? "진행방향이 맞지 않습니다." :
                                    "예약 실패: HTTP "+resp.code();
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ReservationResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "네트워크 오류: "+t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===== 공통 =====
    private void updateDrawerEmpty() {
        if (emptyFavText != null)    emptyFavText.setVisibility(favItems.isEmpty() ? View.VISIBLE : View.GONE);
        if (emptyRecentText != null) emptyRecentText.setVisibility(recentItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private String safeJoin(String a, String mid, String b) {
        String left  = TextUtils.isEmpty(a) ? "" : a;
        String right = TextUtils.isEmpty(b) ? "" : b;
        if (left.isEmpty() && right.isEmpty()) return "";
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + mid + right;
    }


    // 버스 경로 그리기
    private void fetchAndDrawPolylinesForReservation(ReservationResponse r) {
        if (naverMap == null || r == null) return;

        String access = TokenStore.getAccess(getApplicationContext());
        if (TextUtils.isEmpty(access)) return;
        String bearer = "Bearer " + access;

        // 2) 구간 경로 (출발~도착)
        ApiClient.get().getSegment(bearer, r.routeId, r.boardArsId, r.destArsId)
                .enqueue(new Callback<List<RoutePoint>>() {
                    @Override public void onResponse(Call<List<RoutePoint>> call, Response<List<RoutePoint>> res) {
                        if (res.isSuccessful() && res.body()!=null) {
                            drawSegmentPath(res.body());

                            // 예약 직후 화면: 자동 카메라 피팅 대신 "내 위치"로 이동
                            //moveCameraToMyLocation(/*fallbackToSegment=*/true, res.body());
                        }
                    }
                    @Override public void onFailure(Call<List<RoutePoint>> call, Throwable t) { /* ignore */ }
                });
    }

    private void drawSegmentPath(List<RoutePoint> points) {
        if (naverMap == null || points == null || points.isEmpty()) return;

        List<LatLng> latLngs = new ArrayList<>(points.size());
        for (RoutePoint p : points) latLngs.add(new LatLng(p.lat, p.lng));

        if (segmentPathOverlay == null) segmentPathOverlay = new com.naver.maps.map.overlay.PathOverlay();
        segmentPathOverlay.setCoords(latLngs);
        segmentPathOverlay.setWidth(50);         // 좀 더 두껍게
        segmentPathOverlay.setOutlineWidth(3);   // 외곽선으로 강조
        segmentPathOverlay.setOutlineColor(0xFFFFFFFF);
        segmentPathOverlay.setColor(Color.BLUE); // 진한 파란
        segmentPathOverlay.setMap(naverMap);
        segmentPathOverlay.setPatternImage(OverlayImage.fromResource(R.drawable.path_pattern));
        segmentPathOverlay.setPatternInterval(100);
    }

    //카메라 피팅
    private boolean cameraFittedOnce = false;

    private void fitCameraIfNeeded(List<RoutePoint> points) {
        if (cameraFittedOnce || naverMap == null || points == null || points.isEmpty()) return;

        com.naver.maps.geometry.LatLngBounds.Builder b = new com.naver.maps.geometry.LatLngBounds.Builder();
        for (RoutePoint p : points) b.include(new LatLng(p.lat, p.lng));
        com.naver.maps.geometry.LatLngBounds box = b.build();

        naverMap.moveCamera(com.naver.maps.map.CameraUpdate.fitBounds(box, 60)); // padding 60px
        cameraFittedOnce = true;
    }

    //로그아웃 예약 해지시 버스 경로 정리
    private void clearPathOverlays() {
        if (fullPathOverlay != null) { fullPathOverlay.setMap(null); fullPathOverlay = null; }
        if (segmentPathOverlay != null) { segmentPathOverlay.setMap(null); segmentPathOverlay = null; }
        cameraFittedOnce = false;
    }



}

package com.example.testmap.activity;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.adapter.NoticeAdapter;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.TokenStore;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 공지 화면 */
public class NoticeActivity extends AppCompatActivity {

    private ImageButton backBtn;
    private Button btnAll, btnInfo, btnUpdate, btnMaint;

    // 2차 필터(읽음/안읽음)
    private Button btnReadAll, btnReadOnly, btnUnreadOnly;
    private View readFilterScroller; // 통째로 GONE 처리용

    private RecyclerView recycler;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe;

    private NoticeAdapter adapter;
    private ApiService api;

    // 필터/페이징
    @Nullable private String currentType = null; // null=전체
    private int page = 0, size = 20;
    private boolean loading = false, last = false;

    // 읽음 상태 필터
    private enum ReadFilter { ALL, READ, UNREAD }
    private ReadFilter currentReadFilter = ReadFilter.ALL;

    // 유형 카운트
    private long countAll = 0L, countInfo = 0L, countUpdate = 0L, countMaint = 0L;

    // 누적 데이터
    private final List<ApiService.NoticeResp> accum = new ArrayList<>();

    // 로그인 여부
    private boolean isLoggedIn = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice);

        api = ApiClient.get();

        backBtn   = findViewById(R.id.notice_back_button);
        btnAll    = findViewById(R.id.btn_all);
        btnInfo   = findViewById(R.id.btn_info);
        btnUpdate = findViewById(R.id.btn_update);
        btnMaint  = findViewById(R.id.btn_maint);

        btnReadAll    = findViewById(R.id.btn_read_all);
        btnReadOnly   = findViewById(R.id.btn_read_only);
        btnUnreadOnly = findViewById(R.id.btn_unread_only);

        recycler  = findViewById(R.id.recycler);
        swipe     = findViewById(R.id.swipe);

        readFilterScroller = findViewById(R.id.read_filter_scroller);

        backBtn.setOnClickListener(v -> finish());

        adapter = new NoticeAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        recycler.setItemAnimator(animator);
        recycler.setAdapter(adapter);

        // 아이템 펼칠 때: 로그인 상태에서만 읽음 처리/상세조회 호출
        adapter.setOnItemToggle((notice, expanded) -> {
            if (!expanded) return;
            if (!isLoggedIn) return; // 비로그인: 읽음 처리/조회 증가 스킵

            // 로컬 읽음 반영
            markAccumItemRead(notice.id);
            if (currentReadFilter == ReadFilter.UNREAD) {
                applyClientFilterAndShow(true);
            } else {
                updateFilterButtonTexts();
            }

            // 서버 상세(조회수 증가+읽음 처리)
            String bearer = bearerOrNull();
            api.getNoticeDetail(bearer, notice.id, true, true)
                    .enqueue(new Callback<ApiService.NoticeResp>() {
                        @Override public void onResponse(Call<ApiService.NoticeResp> call, Response<ApiService.NoticeResp> resp) { }
                        @Override public void onFailure (Call<ApiService.NoticeResp> call, Throwable t) { }
                    });
        });

        // 1차(유형) 필터
        btnAll.setOnClickListener(v -> selectType(null));
        btnInfo.setOnClickListener(v -> selectType("INFO"));
        btnUpdate.setOnClickListener(v -> selectType("UPDATE"));
        btnMaint.setOnClickListener(v -> selectType("MAINTENANCE"));

        // 2차(읽음) 필터
        btnReadAll.setOnClickListener(v -> {
            currentReadFilter = ReadFilter.ALL;
            styleReadButtons();
            if (accum.isEmpty()) reloadFirstPage(); else applyClientFilterAndShow(true);
        });
        btnReadOnly.setOnClickListener(v -> {
            currentReadFilter = ReadFilter.READ;
            styleReadButtons();
            if (accum.isEmpty()) reloadFirstPage(); else applyClientFilterAndShow(true);
        });
        btnUnreadOnly.setOnClickListener(v -> {
            currentReadFilter = ReadFilter.UNREAD;
            styleReadButtons();
            if (accum.isEmpty()) reloadFirstPage(); else applyClientFilterAndShow(true);
        });

        swipe.setOnRefreshListener(() -> {
            updateLoginDependentUi(); // 로그인 여부 변동 반영
            refreshCounts();
            reloadFirstPage();
        });

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int lastPos = lm.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                if (!loading && !last && lastPos >= total - 3) {
                    loadNextPage();
                }
            }
        });

        // 초기 스타일
        updateFilterButtonTexts();
        styleTypeButtons();
        styleReadButtons();

        // 로그인 의존 UI 초기화
        updateLoginDependentUi();

        // 데이터 초기 로드
        refreshCounts();
        selectType(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLoginDependentUi();
    }

    /** 로그인 여부에 따른 가시성/동작 */
    private void updateLoginDependentUi() {
        String t = TokenStore.getAccess(this);
        isLoggedIn = (t != null && !t.isEmpty());

        if (readFilterScroller != null) {
            readFilterScroller.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        }

        // 카드의 읽음 dot도 로그인시에만 표시
        adapter.setShowReadDot(isLoggedIn);

        if (!isLoggedIn) {
            currentReadFilter = ReadFilter.ALL;
            styleReadButtons();
        }
    }

    /** 현재 항목을 누적 리스트에서 읽음 처리 */
    private void markAccumItemRead(Long id) {
        if (id == null) return;
        for (ApiService.NoticeResp n : accum) {
            if (n != null && id.equals(n.id)) {
                if (n.readAt == null || n.readAt.isEmpty()) n.readAt = "now";
                break;
            }
        }
    }

    /** 유형 필터 변경 → 첫 페이지 로드 */
    private void selectType(@Nullable String type) {
        currentType = type;
        styleTypeButtons();
        reloadFirstPage();
    }

    private void reloadFirstPage() {
        page = 0; last = false;
        accum.clear();
        requestPage(true);
    }

    private void loadNextPage() {
        if (last || loading) return;
        page++;
        requestPage(false);
    }

    @Nullable private String resolveRoleHeaderOrNull() { return null; }

    @Nullable
    private String bearerOrNull() {
        String t = TokenStore.getAccess(this);
        if (t == null || t.isEmpty()) return null;
        return "Bearer " + t;
    }

    private void requestPage(boolean clear) {
        if (loading) return;
        loading = true;
        if (clear && !swipe.isRefreshing()) swipe.setRefreshing(true);

        String bearer = bearerOrNull();
        String roleHeader = resolveRoleHeaderOrNull();
        String sort = "updatedAt,desc";
        String q = null;
        String type = currentType;

        api.getNotices(bearer, roleHeader, page, size, sort, q, type)
                .enqueue(new Callback<ApiService.PageResponse<ApiService.NoticeResp>>() {
                    @Override
                    public void onResponse(Call<ApiService.PageResponse<ApiService.NoticeResp>> call,
                                           Response<ApiService.PageResponse<ApiService.NoticeResp>> resp) {
                        loading = false;
                        swipe.setRefreshing(false);
                        if (!resp.isSuccessful() || resp.body() == null) {
                            Toast.makeText(NoticeActivity.this, "불러오기 실패", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ApiService.PageResponse<ApiService.NoticeResp> body = resp.body();

                        if (body.content != null) accum.addAll(body.content);
                        last = body.last;

                        applyClientFilterAndShow(false);
                    }

                    @Override
                    public void onFailure(Call<ApiService.PageResponse<ApiService.NoticeResp>> call, Throwable t) {
                        loading = false;
                        swipe.setRefreshing(false);
                        Toast.makeText(NoticeActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** 읽음 필터 적용 후 어댑터에 반영 */
    private void applyClientFilterAndShow(boolean fromReadFilterButton) {
        List<ApiService.NoticeResp> filtered = new ArrayList<>();
        for (ApiService.NoticeResp n : accum) {
            if (n == null) continue;
            boolean isRead = n.readAt != null && !n.readAt.isEmpty();
            switch (currentReadFilter) {
                case ALL -> filtered.add(n);
                case READ -> { if (isRead) filtered.add(n); }
                case UNREAD -> { if (!isRead) filtered.add(n); }
            }
        }

        adapter.setData(filtered, true);
        updateFilterButtonTexts();

        if (!loading && !last && filtered.isEmpty() && !fromReadFilterButton) {
            loadNextPage();
        }
    }

    /** 유형 카운트 갱신 */
    private void refreshCounts() {
        final String bearer = bearerOrNull();
        final String roleHeader = resolveRoleHeaderOrNull();
        final String sort = "updatedAt,desc";
        final String q = null;

        api.getNotices(bearer, roleHeader, 0, 1, sort, q, null)
                .enqueue(new CountCb(v -> { countAll = v; updateFilterButtonTexts(); }));
        api.getNotices(bearer, roleHeader, 0, 1, sort, q, "INFO")
                .enqueue(new CountCb(v -> { countInfo = v; updateFilterButtonTexts(); }));
        api.getNotices(bearer, roleHeader, 0, 1, sort, q, "UPDATE")
                .enqueue(new CountCb(v -> { countUpdate = v; updateFilterButtonTexts(); }));
        api.getNotices(bearer, roleHeader, 0, 1, sort, q, "MAINTENANCE")
                .enqueue(new CountCb(v -> { countMaint = v; updateFilterButtonTexts(); }));
    }

    /** 버튼 텍스트 갱신 */
    private void updateFilterButtonTexts() {
        btnAll.setText("전체(" + countAll + ")");
        btnInfo.setText("공지(" + countInfo + ")");
        btnUpdate.setText("업데이트(" + countUpdate + ")");
        btnMaint.setText("점검(" + countMaint + ")");

        long read = 0, unread = 0;
        for (ApiService.NoticeResp n : accum) {
            if (n == null) continue;
            if (n.readAt != null && !n.readAt.isEmpty()) read++; else unread++;
        }
        btnReadAll.setText("전체(" + (read + unread) + ")");
        btnReadOnly.setText("읽음(" + read + ")");
        btnUnreadOnly.setText("안읽음(" + unread + ")");
    }

    /** 1차(유형) 버튼 스타일 */
    private void styleTypeButtons() {
        setBtnStyle(btnAll,   currentType == null);
        setBtnStyle(btnInfo,  "INFO".equals(currentType));
        setBtnStyle(btnUpdate,"UPDATE".equals(currentType));
        setBtnStyle(btnMaint, "MAINTENANCE".equals(currentType));
        // tint 잔존 제거
        btnAll.setBackgroundTintList((ColorStateList) null);
        btnInfo.setBackgroundTintList((ColorStateList) null);
        btnUpdate.setBackgroundTintList((ColorStateList) null);
        btnMaint.setBackgroundTintList((ColorStateList) null);
    }

    /** 2차(읽음) 버튼 스타일 */
    private void styleReadButtons() {
        setBtnStyleSoft(btnReadAll,    currentReadFilter == ReadFilter.ALL);
        setBtnStyleSoft(btnReadOnly,   currentReadFilter == ReadFilter.READ);
        setBtnStyleSoft(btnUnreadOnly, currentReadFilter == ReadFilter.UNREAD);
        btnReadAll.setBackgroundTintList((ColorStateList) null);
        btnReadOnly.setBackgroundTintList((ColorStateList) null);
        btnUnreadOnly.setBackgroundTintList((ColorStateList) null);
    }

    private void setBtnStyle(Button b, boolean on) {
        if (on) { b.setBackgroundResource(R.drawable.bg_filter_blue); b.setTextColor(0xFF1A73E8); }
        else    { b.setBackgroundResource(R.drawable.bg_filter_white); b.setTextColor(0xFF222222); }
    }
    private void setBtnStyleSoft(Button b, boolean on) {
        if (on) { b.setBackgroundResource(R.drawable.bg_filter_blue_soft); b.setTextColor(0xFF1A73E8); }
        else    { b.setBackgroundResource(R.drawable.bg_filter_white);     b.setTextColor(0xFF222222); }
    }

    /** 카운트 콜백 헬퍼 */
    private static class CountCb implements Callback<ApiService.PageResponse<ApiService.NoticeResp>> {
        interface S { void apply(long v); }
        private final S setter;
        CountCb(S s) { this.setter = s; }
        @Override public void onResponse(Call<ApiService.PageResponse<ApiService.NoticeResp>> call,
                                         Response<ApiService.PageResponse<ApiService.NoticeResp>> resp) {
            long v = 0L;
            if (resp.isSuccessful() && resp.body()!=null) v = resp.body().totalElements;
            setter.apply(v);
        }
        @Override public void onFailure(Call<ApiService.PageResponse<ApiService.NoticeResp>> call, Throwable t) {
            setter.apply(0L);
        }
    }
}

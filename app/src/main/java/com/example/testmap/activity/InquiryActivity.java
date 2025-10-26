package com.example.testmap.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.adapter.InquiryAdapter;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.example.testmap.util.TokenStore;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 공개 목록(비로그인 가능, 비밀글은 잠금 표시) + 내 문의만 보기 토글 */
public class InquiryActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private Button btnAll, btnOpen, btnAnswered, btnClosed, btnMineToggle, btnWrite;
    private RecyclerView recycler;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe;
    private LinearLayout viewLoginHint;

    private InquiryAdapter adapter;

    @Nullable private String userId = null;
    @Nullable private String email  = null;
    @Nullable private String role   = null;
    @Nullable private String bearer = null;

    /** 현재 탭 상태: null=전체, OPEN|ANSWERED|CLOSED */
    @Nullable private String curStatus = null;
    private int page = 0, size = 20;

    /** 로딩 상태/마지막 페이지 */
    private boolean loading = false, last = false;

    /** 카운트 표시 */
    private long countAll=0, countOpen=0, countAnswered=0, countClosed=0;

    /** true=내 문의만 보기, false=공개 목록 */
    private boolean mineOnly = false;

    /** 최신 화면 시퀀스 */
    private long listReqSeq = 0L;
    /** loading 플래그가 가리키는 요청 시퀀스 (스피너를 정확히 끄기 위함) */
    private long loadingSeq = -1L;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry);

        btnBack       = findViewById(R.id.btn_back);
        btnAll        = findViewById(R.id.btn_all);
        btnOpen       = findViewById(R.id.btn_open);
        btnAnswered   = findViewById(R.id.btn_answered);
        btnClosed     = findViewById(R.id.btn_closed);
        btnMineToggle = findViewById(R.id.btn_mine);
        btnWrite      = findViewById(R.id.btn_write);
        recycler      = findViewById(R.id.recycler);
        swipe         = findViewById(R.id.swipe);
        viewLoginHint = findViewById(R.id.view_login_hint);

        btnBack.setOnClickListener(v -> finish());

        adapter = new InquiryAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        btnAll.setOnClickListener(v -> selectStatus(null));
        btnOpen.setOnClickListener(v -> selectStatus("OPEN"));
        btnAnswered.setOnClickListener(v -> selectStatus("ANSWERED"));
        btnClosed.setOnClickListener(v -> selectStatus("CLOSED"));

        btnMineToggle.setOnClickListener(v -> {
            if (TextUtils.isEmpty(userId)) {
                Toast.makeText(this, "로그인 후 사용 가능합니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            mineOnly = !mineOnly;
            adapter.setPublicMode(!mineOnly);
            styleButtons();
            refreshAllCountsByMode();
            reloadFirst();
        });

        swipe.setOnRefreshListener(this::reloadFirst);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy<=0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int lastPos = lm.findLastVisibleItemPosition();
                int total   = adapter.getItemCount();
                if (!loading && !last && lastPos >= total-3) loadNext();
            }
        });

        btnWrite.setOnClickListener(v -> startActivity(new Intent(this, InquiryComposeActivity.class)));

        curStatus = null;
        styleButtons();
        applyFilterButtonText();

        initByAuth();
    }

    @Override protected void onResume() {
        super.onResume();
        reloadFirst();
        refreshAllCountsByMode();
    }

    /** 로그인 여부/유저 정보 초기화 */
    private void initByAuth() {
        String access = TokenStore.getAccess(this);
        if (TextUtils.isEmpty(access)) {
            bearer = null;
            userId = email = role = null;
            mineOnly = false;

            adapter.setPublicMode(true);
            btnMineToggle.setVisibility(Button.GONE);
            recycler.setVisibility(android.view.View.VISIBLE);
            viewLoginHint.setVisibility(android.view.View.VISIBLE);

            refreshAllCountsByMode();
            selectStatus(null);
        } else {
            bearer = "Bearer " + access;
            ApiClient.get().me(bearer).enqueue(new Callback<ApiService.UserResponse>() {
                @Override public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> res) {
                    if (!res.isSuccessful() || res.body()==null) {
                        bearer = null;
                        userId = email = role = null;
                        mineOnly = false;
                        adapter.setPublicMode(true);
                        btnMineToggle.setVisibility(Button.GONE);
                        viewLoginHint.setVisibility(android.view.View.VISIBLE);
                        refreshAllCountsByMode();
                        selectStatus(null);
                        return;
                    }
                    ApiService.UserResponse me = res.body();
                    userId = me.userid; email = me.email; role = me.role;

                    btnMineToggle.setVisibility(Button.VISIBLE);
                    viewLoginHint.setVisibility(android.view.View.GONE);

                    adapter.setPublicMode(!mineOnly);
                    refreshAllCountsByMode();
                    selectStatus(null);
                }
                @Override public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                    bearer = null;
                    userId = email = role = null;
                    mineOnly = false;
                    adapter.setPublicMode(true);
                    btnMineToggle.setVisibility(Button.GONE);
                    viewLoginHint.setVisibility(android.view.View.VISIBLE);
                    refreshAllCountsByMode();
                    selectStatus(null);
                }
            });
        }
    }

    /* ----------------- Counts ----------------- */

    private void refreshAllCountsByMode() {
        if (mineOnly) refreshCountsMine();
        else          refreshCountsPublic();
    }

    private void refreshCountsMine() {
        if (bearer == null) { refreshCountsPublic(); return; }
        String sort = "createdAt,desc";
        ApiClient.get().getMyInquiries(bearer, userId, email, role, 0, 1, sort, null, null)
                .enqueue(new CountCb(v -> { countAll = v; applyFilterButtonText(); }));
        ApiClient.get().getMyInquiries(bearer, userId, email, role, 0, 1, sort, null, "OPEN")
                .enqueue(new CountCb(v -> { countOpen = v; applyFilterButtonText(); }));
        ApiClient.get().getMyInquiries(bearer, userId, email, role, 0, 1, sort, null, "ANSWERED")
                .enqueue(new CountCb(v -> { countAnswered = v; applyFilterButtonText(); }));
        ApiClient.get().getMyInquiries(bearer, userId, email, role, 0, 1, sort, null, "CLOSED")
                .enqueue(new CountCb(v -> { countClosed = v; applyFilterButtonText(); }));
    }

    private void refreshCountsPublic() {
        String sort = "createdAt,desc";
        ApiClient.get().getPublicInquiries(0, 1, sort, null, null)
                .enqueue(new CountCb(v -> { countAll = v; applyFilterButtonText(); }));
        ApiClient.get().getPublicInquiries(0, 1, sort, null, "OPEN")
                .enqueue(new CountCb(v -> { countOpen = v; applyFilterButtonText(); }));
        ApiClient.get().getPublicInquiries(0, 1, sort, null, "ANSWERED")
                .enqueue(new CountCb(v -> { countAnswered = v; applyFilterButtonText(); }));
        ApiClient.get().getPublicInquiries(0, 1, sort, null, "CLOSED")
                .enqueue(new CountCb(v -> { countClosed = v; applyFilterButtonText(); }));
    }

    private interface LongCb { void onResult(long v); }
    private static class CountCb implements Callback<ApiService.PageResponse<ApiService.InquiryResp>> {
        private final LongCb cb; CountCb(LongCb cb){ this.cb = cb; }
        @Override public void onResponse(Call<ApiService.PageResponse<ApiService.InquiryResp>> call,
                                         Response<ApiService.PageResponse<ApiService.InquiryResp>> res) {
            long v = 0; if (res.isSuccessful() && res.body()!=null) v = res.body().totalElements;
            if (cb!=null) cb.onResult(v);
        }
        @Override public void onFailure(Call<ApiService.PageResponse<ApiService.InquiryResp>> call, Throwable t) {
            if (cb!=null) cb.onResult(0);
        }
    }

    private void applyFilterButtonText(){
        btnAll.setText("전체(" + countAll + ")");
        btnOpen.setText("접수(" + countOpen + ")");
        btnAnswered.setText("답변(" + countAnswered + ")");
        btnClosed.setText("종료(" + countClosed + ")");
        btnMineToggle.setText(mineOnly ? "내 문의만: 켬" : "내 문의만: 끔");
    }

    /* ----------------- UI/데이터 ----------------- */

    private void selectStatus(@Nullable String st){
        curStatus = st;
        styleButtons();
        refreshAllCountsByMode();
        reloadFirst();
    }

    private void styleButtons(){
        setBtnStyle(btnAll,      curStatus == null);
        setBtnStyle(btnOpen,     "OPEN".equals(curStatus));
        setBtnStyle(btnAnswered, "ANSWERED".equals(curStatus));
        setBtnStyle(btnClosed,   "CLOSED".equals(curStatus));
        setBtnStyle(btnMineToggle, mineOnly);
    }

    private void setBtnStyle(Button b, boolean on){
        if (on){ b.setBackgroundResource(R.drawable.bg_filter_blue); b.setTextColor(0xFF1A73E8); }
        else   { b.setBackgroundResource(R.drawable.bg_filter_white); b.setTextColor(0xFF222222); }
        b.setBackgroundTintList((ColorStateList) null);
    }

    private void reloadFirst(){
        page = 0; last = false;

        // 새 시퀀스로 전환: 이전 로딩 상태 초기화
        loading = false;
        if (!swipe.isRefreshing()) swipe.setRefreshing(false);

        listReqSeq++;
        requestPage(true, listReqSeq);
    }

    private void loadNext(){
        if (last) return;
        requestPage(false, listReqSeq);
    }

    /** 최신 요청만 화면에 반영. 스피너/로딩은 loadingSeq 기준으로 안전하게 해제 */
    private void requestPage(boolean clear, long mySeq){
        // 같은 시퀀스에서만 로딩 중복 방지
        if (loading && loadingSeq == mySeq) return;

        loading = true;
        loadingSeq = mySeq;

        if (clear && !swipe.isRefreshing()) swipe.setRefreshing(true);

        String sort = "createdAt,desc";

        Callback<ApiService.PageResponse<ApiService.InquiryResp>> cb = new Callback<>() {
            @Override public void onResponse(Call<ApiService.PageResponse<ApiService.InquiryResp>> call,
                                             Response<ApiService.PageResponse<ApiService.InquiryResp>> res) {
                // 로딩 해제: 해당 요청이 현재 로딩을 담당했을 때만
                if (mySeq == loadingSeq) {
                    loading = false;
                    swipe.setRefreshing(false);
                }
                // 데이터 반영: 최신 시퀀스일 때만
                if (mySeq != listReqSeq) return;

                if (!res.isSuccessful() || res.body()==null){
                    if (clear) adapter.setData(List.of(), true);
                    Toast.makeText(InquiryActivity.this, "불러오기 실패(" + res.code() + ")", Toast.LENGTH_SHORT).show();
                    return;
                }
                ApiService.PageResponse<ApiService.InquiryResp> body = res.body();
                adapter.setData(body.content, clear);
                last = body.last;

                long te = body.totalElements;
                if (curStatus == null) countAll = te;
                else if ("OPEN".equals(curStatus)) countOpen = te;
                else if ("ANSWERED".equals(curStatus)) countAnswered = te;
                else if ("CLOSED".equals(curStatus)) countClosed = te;
                applyFilterButtonText();
            }
            @Override public void onFailure(Call<ApiService.PageResponse<ApiService.InquiryResp>> call, Throwable t) {
                if (mySeq == loadingSeq) {
                    loading = false;
                    swipe.setRefreshing(false);
                }
                if (mySeq != listReqSeq) return;

                if (clear) adapter.setData(List.of(), true);
                Toast.makeText(InquiryActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
            }
        };

        if (mineOnly) {
            adapter.setPublicMode(false);
            ApiClient.get().getMyInquiries(bearer, userId, email, role, page, size, sort, null, curStatus).enqueue(cb);
        } else {
            adapter.setPublicMode(true);
            ApiClient.get().getPublicInquiries(page, size, sort, null, curStatus).enqueue(cb);
        }
    }
}

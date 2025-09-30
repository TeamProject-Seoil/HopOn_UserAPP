package com.example.testmap.ui;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.activity.BusRouteActivity;
import com.example.testmap.adapter.ArrivalAdapter;
import com.example.testmap.dto.ArrivalDto;
import com.example.testmap.service.ApiClient;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ArrivalsBottomSheet extends BottomSheetDialogFragment {

    public static ArrivalsBottomSheet newInstance(String arsId, String stationName) {
        Bundle b = new Bundle();
        b.putString("arsId", arsId);
        b.putString("stationName", stationName);
        ArrivalsBottomSheet f = new ArrivalsBottomSheet();
        f.setArguments(b);
        return f;
    }

    private final List<ArrivalDto> items = new ArrayList<>();
    private ArrivalAdapter adapter;
    private String arsId;

    // 동적으로 붙일 FAB를 필드로 보관 (remove용)
    private com.google.android.material.floatingactionbutton.FloatingActionButton fab;

    //새로고침 딜레이 걸기
    private static final long REFRESH_COOLDOWN_MS = 10_000L;
    private long lastRefreshAt = 0L;
    private final android.os.Handler uiHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        // ✅ 시트 콘텐츠는 원래대로
        dialog.setContentView(R.layout.bs_arrivals);

        arsId = getArguments() != null ? getArguments().getString("arsId") : "";
        String name = getArguments() != null ? getArguments().getString("stationName") : "";

        TextView tvName = dialog.findViewById(R.id.text_station_name);
        TextView tvArs  = dialog.findViewById(R.id.text_ars_id);
        RecyclerView rv = dialog.findViewById(R.id.recycler_bus_list);
        ImageButton btnClose = dialog.findViewById(R.id.exit_button);

        if (tvName != null) tvName.setText(name);
        if (tvArs  != null) tvArs.setText(arsId);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new ArrivalAdapter(items, item -> {
                if (item.busRouteId == null || item.busRouteId.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), "busRouteId 없음", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                android.content.Intent i = new android.content.Intent(requireContext(), BusRouteActivity.class);
                i.putExtra(BusRouteActivity.EXTRA_BUS_ROUTE_ID,  item.busRouteId);
                i.putExtra(BusRouteActivity.EXTRA_ROUTE_NAME,    item.rtNm);
                i.putExtra(BusRouteActivity.EXTRA_BUS_ROUTETYPE, item.routeType);
                i.putExtra("extra_selected_dir",                 item.adirection);
                i.putExtra(BusRouteActivity.EXTRA_DEPARTURE_NAME, name);
                i.putExtra(BusRouteActivity.EXTRA_DEPARTURE_ARS,  arsId);
                i.putExtra(BusRouteActivity.EXTRA_FOCUS_ARS_ID,    arsId);
                startActivity(i);
            });
            rv.setAdapter(adapter);

            DividerItemDecoration div = new DividerItemDecoration(requireContext(), RecyclerView.VERTICAL);
            Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.divider_arrival);
            if (d != null) div.setDrawable(d);
            rv.addItemDecoration(div);
        }

        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        // ✅ 시트 동작: '디자인' 시트를 기반으로 조절 (정석)
        dialog.setOnShowListener(di -> {
            BottomSheetDialog d = (BottomSheetDialog) di;
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                sheet.requestLayout();

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setDraggable(true);
                behavior.setFitToContents(false);

                int screenH = getResources().getDisplayMetrics().heightPixels;
                int peekPx  = (int) (screenH * 0.40f);
                behavior.setPeekHeight(peekPx, true);
                behavior.setHalfExpandedRatio(0.60f);
                behavior.setExpandedOffset(dp(96));
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }

            // ✅ FAB를 '다이얼로그의 코디네이터'에 동적으로 추가 (시트 위로 떠있음)
            androidx.coordinatorlayout.widget.CoordinatorLayout root =
                    d.findViewById(com.google.android.material.R.id.coordinator);
            if (root != null && fab == null) {
                fab = new com.google.android.material.floatingactionbutton.FloatingActionButton(requireContext());
                fab.setImageResource(R.drawable.ic_refresh_stroke);
                fab.setImageTintList(
                        ColorStateList.valueOf(android.graphics.Color.BLACK) // 아이콘을 검정색으로
                );
                fab.setContentDescription(getString(R.string.refresh));
                fab.setCompatElevation(dp(12));
                fab.setOnClickListener(v -> load());
                fab.setBackgroundTintList(ColorStateList.valueOf(
                        android.graphics.Color.WHITE   // 배경 흰색
                ));
                fab.setCustomSize(dp(72)); // FAB 전체 크기 직접 지정
                fab.setShapeAppearanceModel(
                        com.google.android.material.shape.ShapeAppearanceModel.builder()
                                .setAllCorners(com.google.android.material.shape.CornerFamily.ROUNDED, dp(28)) // 28dp = 56dp/2
                                .build()
                );
                fab.setSize(FloatingActionButton.SIZE_NORMAL);
                fab.setOnClickListener(v -> onRefreshClick());

                // 위치/마진
                CoordinatorLayout.LayoutParams lp =
                        new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                int m = dp(16), mb = dp(24);
                lp.setMargins(m, m, m, mb);
                fab.setLayoutParams(lp);

                root.addView(fab);
            }
        });

        // 데이터 로드
        load();
        return dialog;
    }

    private void onRefreshClick() {
        long now = System.currentTimeMillis();
        long remain = REFRESH_COOLDOWN_MS - (now - lastRefreshAt);

        if (remain > 0) {
            // 쿨다운 중
            Toast.makeText(
                    requireContext(),
                    (remain / 1000) + "초 후에 다시 시도하세요!",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // 새로고침 실행
        lastRefreshAt = now;
        Toast.makeText(requireContext(), "새로고침 실행!", Toast.LENGTH_SHORT).show();
        load();
    }

    private void load() {
        ApiClient.get().getStationArrivals(arsId).enqueue(new Callback<List<ArrivalDto>>() {
            @Override public void onResponse(Call<List<ArrivalDto>> call, Response<List<ArrivalDto>> res) {
                if (!isAdded()) return;
                if (!res.isSuccessful() || res.body() == null) {
                    android.widget.Toast.makeText(requireContext(), "HTTP " + res.code(), android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                items.clear();
                items.addAll(res.body());
                if (adapter != null) adapter.notifyDataSetChanged();
            }
            @Override public void onFailure(Call<List<ArrivalDto>> call, Throwable t) {
                if (!isAdded()) return;
                android.widget.Toast.makeText(requireContext(), "네트워크 오류: " + t.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // ✅ 메모리/중복 방지: FAB 제거
        Dialog dlg = getDialog();
        if (dlg instanceof BottomSheetDialog) {
            CoordinatorLayout root = ((BottomSheetDialog) dlg).findViewById(com.google.android.material.R.id.coordinator);
            if (root != null && fab != null) {
                root.removeView(fab);
            }
        }
        fab = null;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

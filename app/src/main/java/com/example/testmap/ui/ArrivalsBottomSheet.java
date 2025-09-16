package com.example.testmap.ui;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import androidx.recyclerview.widget.DividerItemDecoration;


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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bs_arrivals, container, false);

        TextView tvName = v.findViewById(R.id.text_station_name);
        TextView tvArs  = v.findViewById(R.id.text_ars_id);
        RecyclerView rv = v.findViewById(R.id.recycler_bus_list);
        ImageButton btnRefresh = v.findViewById(R.id.refresh_button);
        ImageButton btnClose   = v.findViewById(R.id.exit_button);

        arsId = getArguments() != null ? getArguments().getString("arsId") : "";
        String name = getArguments() != null ? getArguments().getString("stationName") : "";
        tvName.setText(name);
        tvArs.setText(arsId);

        // RV 세팅 (한 번만)
        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        rv.setLayoutManager(lm);

        adapter = new ArrivalAdapter(items, item -> {
            // ★ 여기서 busRouteId(or busRoute)로 화면 전환
            if (item.busRouteId == null || item.busRouteId.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "busRouteId 없음", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            android.content.Intent i = new android.content.Intent(
                    requireContext(), com.example.testmap.activity.BusRouteActivity.class);
            i.putExtra(com.example.testmap.activity.BusRouteActivity.EXTRA_BUS_ROUTE_ID, item.busRouteId);
            i.putExtra(com.example.testmap.activity.BusRouteActivity.EXTRA_ROUTE_NAME,  item.rtNm);
            i.putExtra(BusRouteActivity.EXTRA_BUS_ROUTETYPE,  item.routeType);
            i.putExtra("extra_selected_dir", item.adirection);
            startActivity(i);
            // 필요하면 아래 주석 해제해서 바텀시트 닫기
            // dismiss();
        });
        rv.setAdapter(adapter);



        // 구분선
        DividerItemDecoration div = new DividerItemDecoration(requireContext(), lm.getOrientation());
        Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.divider_arrival);
        if (d != null) div.setDrawable(d);
        rv.addItemDecoration(div);

        btnRefresh.setOnClickListener(view -> load());
        btnClose.setOnClickListener(view -> dismiss());

        load();
        return v;
    }

    private void load() {
        ApiClient.get().getStationArrivals(arsId).enqueue(new Callback<List<ArrivalDto>>() {
            @Override public void onResponse(Call<List<ArrivalDto>> call, Response<List<ArrivalDto>> res) {
                if (!isAdded()) return;
                if (!res.isSuccessful() || res.body() == null) {
                    android.widget.Toast.makeText(requireContext(), "HTTP "+res.code(), android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                items.clear();
                items.addAll(res.body());
                adapter.notifyDataSetChanged();
            }
            @Override public void onFailure(Call<List<ArrivalDto>> call, Throwable t) {
                if (!isAdded()) return;
                android.widget.Toast.makeText(requireContext(), "네트워크 오류: "+t.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(di -> {
            BottomSheetDialog d = (BottomSheetDialog) di;
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;

            sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            sheet.requestLayout();

            //sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(), sheet.getPaddingRight(), 0);

            BottomSheetBehavior<View> b = BottomSheetBehavior.from(sheet);
            b.setDraggable(true);
            b.setFitToContents(false);

            int screenH = getResources().getDisplayMetrics().heightPixels;
            int peekPx  = (int) (screenH * 0.40f);
            b.setPeekHeight(peekPx, true);

            b.setHalfExpandedRatio(0.60f);
            b.setExpandedOffset(dp(96));

            b.setState(BottomSheetBehavior.STATE_COLLAPSED);
        });

        return dialog;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

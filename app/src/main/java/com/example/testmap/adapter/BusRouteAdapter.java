package com.example.testmap.adapter;

import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.example.testmap.R;
import com.example.testmap.dto.BusRouteDto;
import java.util.*;

public class BusRouteAdapter extends RecyclerView.Adapter<BusRouteAdapter.VH> {

    private final Map<String, Float> progressByNextSeq = new HashMap<>();
    private final List<BusRouteDto> items = new ArrayList<>();

    // ===== 선택 상태 =====

    // ===== 출발역 강조용 필드(NEW) =====
    @Nullable private String departureArsId = null;
    public void setDepartureArsId(@Nullable String arsId) {
        this.departureArsId = arsId;
        notifyDataSetChanged(); // 출발역 표시 업데이트
    }
    //private int selectedPos = RecyclerView.NO_POSITION;

    /*public int getSelectedPos() {
        return selectedPos;
    }*/

    /*public void setSelectedPos(int pos) {
        if (pos == selectedPos) return;
        int old = selectedPos;
        selectedPos = pos;

        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
        if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
    }*/

    /** seq 값으로 선택하고 싶을 때 호출 (없으면 선택 해제) */
    /*public void selectBySeq(@NonNull String seq) {
        Integer p = findPosBySeq(safeParse(seq));
        setSelectedPos(p == null ? RecyclerView.NO_POSITION : p);
    }*/

    private static int safeParse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return Integer.MIN_VALUE; }
    }
    // =====================

    // 클릭 이벤트 전달용
    public interface OnStopClickListener { void onStopClick(BusRouteDto stop); }
    private OnStopClickListener listener;
    public void setOnStopClickListener(OnStopClickListener l) { this.listener = l; }

    // 오버레이용 버스 마크
    public static class BusMark {
        public final int nextSeq; public final float t;
        public final String number, congestion; public final boolean lowFloor;
        public BusMark(int nextSeq, float t, String number, String congestion, boolean lowFloor) {
            this.nextSeq = nextSeq; this.t = t; this.number = number;
            this.congestion = congestion; this.lowFloor = lowFloor;
        }
    }
    private final List<BusMark> marks = new ArrayList<>();
    public void setBusMarks(List<BusMark> m) { marks.clear(); if (m!=null) marks.addAll(m); }
    public List<BusMark> getBusMarks() { return marks; }

    // seq -> 어댑터 포지션
    public Integer findPosBySeq(int seq) {
        for (int i = 0; i < items.size(); i++) {
            try { if (Integer.parseInt(items.get(i).seq) == seq) return i; }
            catch (Exception ignore) {}
        }
        return null;
    }

    public void submit(List<BusRouteDto> data) {
        items.clear(); items.addAll(data);
        // selectedPos = RecyclerView.NO_POSITION; // 사용 안 함
        notifyDataSetChanged();
    }

    public void setBusProgress(Map<String, Float> m) {
        progressByNextSeq.clear();
        if (m != null) progressByNextSeq.putAll(m);
        notifyDataSetChanged();
    }

    @Nullable
    public Float progressFor(String seqKey) { return progressByNextSeq.get(seqKey); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View view = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_bus_route, p, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        BusRouteDto it = items.get(pos);
        h.name.setText(it.stationNm);
        h.ars.setText(it.arsId);
        h.itemView.setTag(R.id.tag_seq, it.seq);

        // ✅ 출발역인지 여부로 강조 판단(클릭과 무관하게 고정)
        boolean isDeparture = (departureArsId != null && departureArsId.equals(it.arsId));
        h.myLoc.setVisibility(isDeparture ? View.VISIBLE : View.GONE);
        h.itemView.setActivated(isDeparture);
        h.name.setTypeface(null, isDeparture ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);

        // ✅ 클릭해도 출발 강조가 바뀌지 않게: setSelectedPos() 호출 제거
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION && listener != null) {
                listener.onStopClick(items.get(p)); // 예약 다이얼로그 등 동작만 수행
            }
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, ars;
        View line;            // 타임라인 뷰(id: R.id.line)  ⬅ 데코에서 중심 x 계산에 사용 가능
        ImageView dot, myLoc;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tvStopName);
            ars  = v.findViewById(R.id.tvArsId);
            line = v.findViewById(R.id.line);
            dot  = v.findViewById(R.id.dot);
            myLoc   = v.findViewById(R.id.ivMyLocation); // ⬅ 추가
        }
    }

    @Override
    public int getItemCount() { return items.size(); }
}


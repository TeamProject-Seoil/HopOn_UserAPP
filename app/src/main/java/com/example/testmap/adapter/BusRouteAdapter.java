package com.example.testmap.adapter;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.testmap.R;
import com.example.testmap.dto.BusRouteDto;
import java.util.*;

public class BusRouteAdapter extends RecyclerView.Adapter<BusRouteAdapter.VH> {

    private final Map<String, Float> progressByNextSeq = new HashMap<>();
    private final List<BusRouteDto> items = new ArrayList<>();

    private int timelineColor = 0;

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

        // ✅ 세로 라인 색
        if (h.line != null && timelineColor != 0) {
            h.line.setBackgroundColor(timelineColor);
        }

        // ✅ 점(dot) 색: layer-list의 바깥 원 stroke를 routeType 색으로
        if (h.dot != null && timelineColor != 0) {
            tintDot(h.dot, timelineColor);
        }

        // ✅ 출발역 강조
        boolean isDeparture = (departureArsId != null && departureArsId.equals(it.arsId));
        h.myLoc.setVisibility(isDeparture ? View.VISIBLE : View.GONE);
        h.itemView.setActivated(isDeparture);
        h.name.setTypeface(null, isDeparture ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);

        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION && listener != null) {
                listener.onStopClick(items.get(p));
            }
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, ars;
        View line;            // 타임라인 뷰(id: R.id.line)  ⬅ 데코에서 중심 x 계산에 사용 가능
        ImageView dot, myLoc, busMark;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tvStopName);
            ars  = v.findViewById(R.id.tvArsId);
            line = v.findViewById(R.id.line);
            dot  = v.findViewById(R.id.dot);
            myLoc   = v.findViewById(R.id.ivMyLocation); // ⬅ 추가
            busMark= v.findViewById(R.id.busMark);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void setTimelineColor(int color) {
        this.timelineColor = color;
        notifyDataSetChanged();
    }

    // ⬇⬇⬇ 추가: 점 색 변경 헬퍼
    private void tintDot(@NonNull ImageView dotView, int color) {
        // 1) src가 있으면 src부터 사용, 없으면 background 사용
        Drawable raw = dotView.getDrawable() != null ? dotView.getDrawable() : dotView.getBackground();
        if (raw == null) return;

        // 재활용 이슈 방지 (shared state 오염 방지)
        Drawable d = raw.getConstantState() != null
                ? raw.getConstantState().newDrawable().mutate()
                : raw.mutate();

        if (d instanceof LayerDrawable) {
            LayerDrawable ld = (LayerDrawable) d;

            // a) 바깥 원 stroke 색 변경
            Drawable outer = ld.findDrawableByLayerId(R.id.dot_outer);
            if (outer instanceof GradientDrawable) {
                GradientDrawable g = (GradientDrawable) outer;
                int strokePx = (int) dp(dotView, 2);   // 2dp
                g.setStroke(strokePx, color);
                // 채움은 흰색 유지 (원하면 g.setColor(color)로 채움 가능)
            }

            // b) 안쪽 원 색 바꾸고 싶다면 (지금은 흰색 유지)
            // Drawable inner = ld.findDrawableByLayerId(R.id.dot_inner);
            // if (inner instanceof GradientDrawable) {
            //     ((GradientDrawable) inner).setColor(Color.WHITE);
            // }

            // src/배경 어디에 있었는지에 맞춰 되돌려주기
            if (dotView.getDrawable() != null) {
                dotView.setImageDrawable(ld);
            } else {
                dotView.setBackground(ld);
            }
        } else {
            // layer-list가 아닌 벡터/PNG라면 전체 틴트 (흰색 영역까지 물들 수 있음)
            Drawable tinted = d;
            DrawableCompat.setTint(tinted, color);
            if (dotView.getDrawable() != null) {
                dotView.setImageDrawable(tinted);
            } else {
                dotView.setBackground(tinted);
            }
        }
    }

    private float dp(@NonNull View v, float dp) {
        return dp * v.getResources().getDisplayMetrics().density;
    }

    public int getTimelineColor() {
        return timelineColor;
    }

}


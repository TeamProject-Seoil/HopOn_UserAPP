// BusRouteAdapter.java
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



    // 오버레이가 그릴 버스 마크들
    public static class BusMark {
        public final int nextSeq;      // 다음 정류장 seq
        public final float t;          // 0~1 (아이템 내부 위치)
        public final String number;    // "2848" 같은 표시 번호
        public final String congestion;// "여유","보통","혼잡","매우혼잡" 또는 ""
        public final boolean lowFloor; // 저상 여부
        public BusMark(int nextSeq, float t, String number, String congestion, boolean lowFloor) {
            this.nextSeq = nextSeq; this.t = t; this.number = number;
            this.congestion = congestion; this.lowFloor = lowFloor;
        }
    }
    private final List<BusMark> marks = new ArrayList<>();
    public void setBusMarks(List<BusMark> m) { marks.clear(); if (m!=null) marks.addAll(m); }
    public List<BusMark> getBusMarks() { return marks; }

    // seq→어댑터 포지션 찾기 (오버레이에서 사용)
    public Integer findPosBySeq(int seq) {
        for (int i=0;i<items.size();i++) {
            try { if (Integer.parseInt(items.get(i).seq) == seq) return i; } catch (Exception ignore){}
        }
        return null;
    }
    public void submit(List<BusRouteDto> data) {
        items.clear(); items.addAll(data);
        notifyDataSetChanged();
    }
    public void setBusProgress(Map<String, Float> m) {
        progressByNextSeq.clear();
        if (m != null) progressByNextSeq.putAll(m);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View view = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_bus_route, p, false);
        return new VH(view);
    }
    @Nullable
    public Float progressFor(String seqKey) {
        return progressByNextSeq.get(seqKey);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        BusRouteDto it = items.get(pos);
        h.name.setText(it.stationNm);
        h.ars.setText(it.arsId);

        h.itemView.setTag(R.id.tag_seq, it.seq);

    }


    static class VH extends RecyclerView.ViewHolder {
        TextView name, ars;
        View line;
        ImageView dot;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tvStopName);
            ars  = v.findViewById(R.id.tvArsId);
            line = v.findViewById(R.id.line);
            dot  = v.findViewById(R.id.dot);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

}

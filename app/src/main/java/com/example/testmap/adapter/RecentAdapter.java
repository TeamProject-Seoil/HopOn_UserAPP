// app/src/main/java/com/example/testmap/adapter/RecentAdapter.java
package com.example.testmap.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.model.RecentItem;

import java.util.List;
import java.util.Locale;

public class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.ViewHolder> {

    private static final String TAG = "RecentAdapter";
    private final List<RecentItem> recentList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(RecentItem item);
        void onAddFavClick(RecentItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public RecentAdapter(List<RecentItem> recentList) {
        this.recentList = recentList;
        setHasStableIds(false);
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_bus, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        // 재활용 잔상 방지: tint/alpha 초기화
        ViewCompat.setBackgroundTintList(h.busNumber, null);
        h.busNumber.setAlpha(1f);

        RecentItem item = recentList.get(position);
        try {
            h.busNumber.setText(item.getBusNumber());
            h.startStation.setText("승차: " + safe(item.getStartStation()));
            h.endStation.setText("하차: " + safe(item.getEndStation()));

            // 노선유형: routeTypeName 우선, 없으면 코드→라벨
            String typeStr = isEmpty(item.getRouteTypeName())
                    ? toRouteTypeLabel(item.getBusRouteType())
                    : item.getRouteTypeName();

            int color = colorForRoute(typeStr);
            applyBackgroundColor(h.busNumber, color);
            h.busNumber.setTextColor(contrastingTextColor(color)); // 가독성 보정

        } catch (Throwable t) {
            // 방어적 로깅: 어떤 값 때문에 깨지는지 확인용
            Log.e(TAG, "bind error at pos=" + position, t);
        }

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onItemClick(item); });
        h.addFavIcon.setOnClickListener(v -> { if (listener != null) listener.onAddFavClick(item); });
    }

    @Override
    public int getItemCount() {
        return (recentList == null) ? 0 : recentList.size();
    }

    // ===== Helpers =====

    private static String safe(String s){ return s == null ? "" : s; }
    private static boolean isEmpty(String s){ return s == null || s.trim().isEmpty(); }

    private void applyBackgroundColor(@NonNull TextView tv, int color) {
        Drawable bg = tv.getBackground();
        if (bg != null) {
            Drawable m = bg.mutate();
            if (m instanceof GradientDrawable) {
                ((GradientDrawable) m).setColor(color);
            } else {
                ViewCompat.setBackgroundTintList(tv, ColorStateList.valueOf(color));
            }
        } else {
            tv.setBackgroundColor(color);
        }
    }

    /** 대비 텍스트 색 (밝은 배경=검정, 어두운 배경=흰색) */
    private int contrastingTextColor(int bg) {
        double r = ((bg >> 16) & 0xFF) / 255.0;
        double g = ((bg >> 8) & 0xFF) / 255.0;
        double b = (bg & 0xFF) / 255.0;

        r = (r <= 0.03928) ? r/12.92 : Math.pow((r+0.055)/1.055, 2.4);
        g = (g <= 0.03928) ? g/12.92 : Math.pow((g+0.055)/1.055, 2.4);
        b = (b <= 0.03928) ? b/12.92 : Math.pow((b+0.055)/1.055, 2.4);

        double L = 0.2126*r + 0.7152*g + 0.0722*b;
        return (L > 0.5) ? Color.BLACK : Color.WHITE;
    }

    /** 서버 코드 → 라벨 */
    private String toRouteTypeLabel(Integer code) {
        if (code == null) return null;
        switch (code) {
            case 3: return "간선";
            case 4: return "지선";
            case 5: return "순환";
            case 6: return "광역";
            case 2: return "마을";
            case 8: return "경기";
            case 1: return "공항";
            case 7: return "인천";
            case 0: return "공용";
            default: return null;
        }
    }

    /** 라벨/별칭 → 색상 (미지정/이상치면 기본 초록) */
    private int colorForRoute(String raw) {
        if (raw == null) return Color.parseColor("#42A05B");
        String s = raw.trim();
        String u = s.toUpperCase(Locale.ROOT);

        switch (s) {
            case "간선": return Color.parseColor("#2B7DE9");   // 파랑 - 간선버스 (Main/Trunk Line)
            case "지선": return Color.parseColor("#42A05B");   // 초록 - 지선버스 (Branch Line)
            case "광역": return Color.parseColor("#D2473B");   // 빨강 - 광역버스 (Express/Metropolitan)
            case "순환": return Color.parseColor("#E3B021");   // 노랑 - 순환버스 (Circular)
            case "마을": return Color.parseColor("#63A27E"); // 연한 초록(contrast≈3.0)
            case "경기": return Color.parseColor("#42A05B");   // 초록 - 경기도 버스 (Gyeonggi)
            case "공항": return Color.parseColor("#F57C00");   // 주황 - 공항버스 (Airport)
            case "인천": return Color.parseColor("#00A2E8");   // 하늘색 - 인천버스 (Incheon)
            case "공용": return Color.parseColor("#607D8B");   // 회색 - 공용/기타 (Shared or Misc.)
        }

        switch (u) {
            case "BLUE": case "TRUNK":      return Color.parseColor("#2B7DE9");
            case "GREEN": case "BRANCH":
            case "VILLAGE":                 return Color.parseColor("#42A05B");
            case "RED": case "EXPRESS":     return Color.parseColor("#D2473B");
            case "YELLOW": case "CIRCULAR": return Color.parseColor("#E3B021");
            case "AIRPORT":                 return Color.parseColor("#7A52CC");
        }

        return Color.parseColor("#42A05B");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView busNumber, startStation, endStation;
        ImageView addFavIcon;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            busNumber    = itemView.findViewById(R.id.bus_number_recent);
            startStation = itemView.findViewById(R.id.start_station);
            endStation   = itemView.findViewById(R.id.end_station);
            addFavIcon   = itemView.findViewById(R.id.add_to_fav);
        }
    }
}

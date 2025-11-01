package com.example.testmap.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;

import com.example.testmap.R;
import com.example.testmap.model.FavoriteItem;

import java.util.ArrayList;
import java.util.List;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

    public interface OnFavoriteClickListener {
        void onClickItem(FavoriteItem item, int position);
        void onClickUnstar(FavoriteItem item, int position);
    }

    private final List<FavoriteItem> favoriteList = new ArrayList<>();
    private OnFavoriteClickListener listener;

    public FavoriteAdapter() { }

    public FavoriteAdapter(List<FavoriteItem> init) {
        if (init != null) {
            favoriteList.clear();
            favoriteList.addAll(init);
        }
    }

    /** 전체 교체 */
    public void setItems(List<FavoriteItem> items) {
        favoriteList.clear();
        if (items != null) favoriteList.addAll(items);
        notifyDataSetChanged();
    }

    /** 최상단 삽입 */
    public void insertAtTop(@NonNull FavoriteItem item) {
        favoriteList.add(0, item);
        notifyItemInserted(0);
    }

    /** 지정 위치 제거 */
    public void removeAt(int position) {
        if (position < 0 || position >= favoriteList.size()) return;
        favoriteList.remove(position);
        notifyItemRemoved(position);
    }

    /** 안전 접근 */
    public FavoriteItem getItem(int position) {
        if (position < 0 || position >= favoriteList.size()) return null;
        return favoriteList.get(position);
    }

    public void setOnFavoriteClickListener(OnFavoriteClickListener l) {
        this.listener = l;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_fav_bus, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        // 재활용 상태 초기화
        h.itemView.animate().cancel();
        h.itemView.setAlpha(1f);
        h.itemView.setScaleX(1f);
        h.itemView.setScaleY(1f);

        FavoriteItem item = favoriteList.get(position);
        h.busNumberBox.setText(nullSafe(item.getBusNumber()));
        h.busName.setText(nullSafe(item.getBusName()));
        h.busInfo.setText(nullSafe(item.getBusInfo()));

        // ---- 노선유형 색상/라벨 적용 (직접 게터 사용) ----
        Integer routeTypeCode = item.getBusRouteType();  // ← 기존: reflectInt(...)
        String  routeTypeName = item.getRouteTypeName(); // ← 기존: reflectString(...)

        applyRouteTypeStyle(h, routeTypeCode, routeTypeName);

        h.itemView.setOnClickListener(v -> {
            if (listener == null) return;
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            listener.onClickItem(favoriteList.get(pos), pos);
        });

        h.favIcon.setOnClickListener(v -> {
            if (listener == null) return;
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            listener.onClickUnstar(favoriteList.get(pos), pos);
        });
    }

    @Override public int getItemCount() { return favoriteList.size(); }

    @Override
    public void onViewRecycled(@NonNull ViewHolder h) {
        h.itemView.animate().cancel();
        h.itemView.setAlpha(1f);
        h.itemView.setScaleX(1f);
        h.itemView.setScaleY(1f);
        super.onViewRecycled(h);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView busNumberBox, busName, busInfo;
        ImageView favIcon;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            busNumberBox = itemView.findViewById(R.id.bus_number_box);
            busName      = itemView.findViewById(R.id.bus_name);
            busInfo      = itemView.findViewById(R.id.bus_info);
            favIcon      = itemView.findViewById(R.id.fav_icon);
        }
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    // =========================
    // 노선유형 색상 팔레트 & 적용
    // =========================
    private void applyRouteTypeStyle(ViewHolder h, Integer code, String name) {
        // 1) 라벨 우선, 코드 보조
        String hex = "#4B93FF"; // 기본 파랑
        if (!TextUtils.isEmpty(name)) {
            switch (name) {
                case "간선": hex = "#2B7DE9"; break;
                case "지선": hex = "#42A05B"; break;
                case "순환": hex = "#E3B021"; break;
                case "광역": hex = "#D2473B"; break;
                case "공항": hex = "#FF8F00"; break;
                case "마을": hex = "#63A27E"; break;
                case "경기": hex = "#00A88F"; break;
                case "인천": hex = "#00A0E9"; break;
                case "급행":
                case "좌석": hex = "#8E24AA"; break;
                case "공용": hex = "#6E7E91"; break;
                case "폐지": hex = "#9E9E9E"; break;
            }
        } else if (code != null) {
            switch (code) {
                case 3: hex = "#2B7DE9"; break; // 간선
                case 4: hex = "#42A05B"; break; // 지선
                case 5: hex = "#E3B021"; break; // 순환
                case 6: hex = "#D2473B"; break; // 광역
                case 1: hex = "#FF8F00"; break; // 공항
                case 2: hex = "#43A047"; break; // 마을
                case 7: hex = "#00A0E9"; break; // 인천
                case 8: hex = "#00A88F"; break; // 경기
                case 9: hex = "#9E9E9E"; break; // 폐지
                case 0:
                default: hex = "#6E7E91"; break; // 공용/기타
            }
        }
        int bg = Color.parseColor(hex);

        // 2) 번호 박스 배경 틴트
        if (h.busNumberBox.getBackground() != null) {
            ViewCompat.setBackgroundTintList(h.busNumberBox, ColorStateList.valueOf(bg));
        } else {
            h.busNumberBox.setBackgroundColor(bg);
        }

        // 3) 대비에 따라 텍스트 색
        h.busNumberBox.setTextColor(shouldUseDarkText(bg) ? Color.BLACK : Color.WHITE);

        // 4) 보조 텍스트에 라벨 덧붙이기(중복 방지)
        if (!TextUtils.isEmpty(name)) {
            String base = nullSafe(String.valueOf(h.busInfo.getText()));
            if (TextUtils.isEmpty(base)) h.busInfo.setText(name);
            else if (!base.contains(name)) h.busInfo.setText(base + " · " + name);
        }
    }

    /** 배경이 밝으면 검정, 어두우면 흰 텍스트 */
    private boolean shouldUseDarkText(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        r = (r <= 0.03928) ? r/12.92 : Math.pow((r+0.055)/1.055, 2.4);
        g = (g <= 0.03928) ? g/12.92 : Math.pow((g+0.055)/1.055, 2.4);
        b = (b <= 0.03928) ? b/12.92 : Math.pow((b+0.055)/1.055, 2.4);
        double luminance = 0.2126*r + 0.7152*g + 0.0722*b;
        return luminance > 0.6;
    }
}

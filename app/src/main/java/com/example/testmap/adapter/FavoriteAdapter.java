// app/src/main/java/com/example/testmap/adapter/FavoriteAdapter.java
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
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.model.FavoriteItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FavoriteAdapter extends ListAdapter<FavoriteItem, FavoriteAdapter.ViewHolder> {

    public interface OnFavoriteClickListener {
        void onClickItem(FavoriteItem item, int position);
        void onClickUnstar(FavoriteItem item, int position);
    }

    private OnFavoriteClickListener listener;

    public FavoriteAdapter() {
        super(DIFF_CALLBACK);
        setHasStableIds(true);
    }

    // ===== DiffUtil: 항목 동일성 + 내용 동일성 =====
    private static final DiffUtil.ItemCallback<FavoriteItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull FavoriteItem a, @NonNull FavoriteItem b) {
                    // 모델에 id가 없다면, 화면에 유일한 조합으로 대체(버스번호+경로 텍스트 등)
                    return safe(a.getBusNumber()).equals(safe(b.getBusNumber())) &&
                            safe(a.getBusName()).equals(safe(b.getBusName())) &&
                            safe(a.getBusInfo()).equals(safe(b.getBusInfo()));
                }
                @Override
                public boolean areContentsTheSame(@NonNull FavoriteItem a, @NonNull FavoriteItem b) {
                    return  eq(a.getBusNumber(), b.getBusNumber())
                            && eq(a.getBusName(),   b.getBusName())
                            && eq(a.getBusInfo(),   b.getBusInfo())
                            && eq(a.getRouteTypeName(), b.getRouteTypeName())
                            && eq(a.getBusRouteType(),  b.getBusRouteType());
                }
            };

    private static boolean eq(Object x, Object y){ return x==y || (x!=null && x.equals(y)); }
    private static String safe(String s){ return s==null? "": s; }

    public void setOnFavoriteClickListener(OnFavoriteClickListener l) { this.listener = l; }

    /** 편의: 전체 교체는 submitList로, 내부에서 새 리스트 복사 */
    public void setItems(List<FavoriteItem> items) {
        submitList(items==null ? List.of() : new ArrayList<>(items));
    }

    /** 편의: 최상단 삽입(낙관적 UI 업데이트용) */
    public void insertAtTop(@NonNull FavoriteItem item) {
        List<FavoriteItem> cur = new ArrayList<>(getCurrentList());
        cur.add(0, item);
        submitList(cur);
    }

    /** 편의: 위치 제거(낙관적 UI 업데이트용) */
    public void removeAt(int position) {
        if (position < 0 || position >= getItemCount()) return;
        List<FavoriteItem> cur = new ArrayList<>(getCurrentList());
        cur.remove(position);
        submitList(cur);
    }

    @Override public long getItemId(int position) {
        // 안정 id: 표시필드 해시로 생성(모델에 id가 생기면 그걸로 바꾸면 됨)
        FavoriteItem it = getItem(position);
        String key = safe(it.getBusNumber()) + "|" + safe(it.getBusName()) + "|" + safe(it.getBusInfo());
        return key.hashCode();
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

        FavoriteItem item = getItem(position);
        h.busNumberBox.setText(safe(item.getBusNumber()));
        h.busName.setText(safe(item.getBusName()));
        h.busInfo.setText(safe(item.getBusInfo()));

        applyRouteTypeStyle(h, item.getBusRouteType(), item.getRouteTypeName());

        h.itemView.setOnClickListener(v -> {
            if (listener == null) return;
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            listener.onClickItem(getItem(pos), pos);
        });

        h.favIcon.setOnClickListener(v -> {
            if (listener == null) return;
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            listener.onClickUnstar(getItem(pos), pos);
        });
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

    private void applyRouteTypeStyle(ViewHolder h, Integer code, String name) {
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
                case 3: hex = "#2B7DE9"; break;
                case 4: hex = "#42A05B"; break;
                case 5: hex = "#E3B021"; break;
                case 6: hex = "#D2473B"; break;
                case 1: hex = "#FF8F00"; break;
                case 2: hex = "#43A047"; break;
                case 7: hex = "#00A0E9"; break;
                case 8: hex = "#00A88F"; break;
                case 9: hex = "#9E9E9E"; break;
                default: hex = "#6E7E91"; break;
            }
        }
        int bg = Color.parseColor(hex);

        if (h.busNumberBox.getBackground() != null) {
            ViewCompat.setBackgroundTintList(h.busNumberBox, ColorStateList.valueOf(bg));
        } else {
            h.busNumberBox.setBackgroundColor(bg);
        }
        h.busNumberBox.setTextColor(shouldUseDarkText(bg) ? Color.BLACK : Color.WHITE);

        if (!TextUtils.isEmpty(name)) {
            String base = safe(String.valueOf(h.busInfo.getText()));
            if (TextUtils.isEmpty(base)) h.busInfo.setText(name);
            else if (!base.contains(name)) h.busInfo.setText(base + " · " + name);
        }
    }

    private boolean shouldUseDarkText(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        r = (r <= 0.03928) ? r/12.92 : Math.pow((r+0.055)/1.055, 2.4);
        g = (g <= 0.03928) ? g/12.92 : Math.pow((g+0.055)/1.055, 2.4);
        b = (b <= 0.03928) ? b/12.92 : Math.pow((b+0.055)/1.055, 2.4);
        double L = 0.2126*r + 0.7152*g + 0.0722*b;
        return L > 0.6;
    }
}

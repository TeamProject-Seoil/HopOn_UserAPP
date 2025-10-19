package com.example.testmap.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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

    /** 전체 교체가 필요한 경우에만 사용 (가능하면 단건 메서드 사용 권장) */
    public void setItems(List<FavoriteItem> items) {
        favoriteList.clear();
        if (items != null) favoriteList.addAll(items);
        notifyDataSetChanged();
    }

    /** ✅ 리스트 최상단에 아이템 삽입 (애니메이션 발생) */
    public void insertAtTop(@NonNull FavoriteItem item) {
        favoriteList.add(0, item);
        notifyItemInserted(0);
    }

    /** ✅ 지정 위치 아이템 제거 (애니메이션 발생) */
    public void removeAt(int position) {
        if (position < 0 || position >= favoriteList.size()) return;
        favoriteList.remove(position);
        notifyItemRemoved(position);
    }

    /** ✅ 안전하게 아이템 가져오기 */
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
    public void onBindViewHolder(@NonNull ViewHolder h, int positionIgnored) {
        FavoriteItem item = favoriteList.get(h.getBindingAdapterPosition());

        h.busNumberBox.setText(item.getBusNumber());
        h.busName.setText(item.getBusName());
        h.busInfo.setText(item.getBusInfo());

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
}

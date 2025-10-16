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

import java.util.List;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

    private final List<FavoriteItem> favoriteList;

    public FavoriteAdapter(List<FavoriteItem> favoriteList) {
        this.favoriteList = favoriteList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_fav_bus, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoriteItem item = favoriteList.get(position);

        holder.busNumberBox.setText(item.getBusNumber());
        holder.busName.setText(item.getBusName());
        holder.busInfo.setText(item.getBusInfo());
    }

    @Override
    public int getItemCount() {
        return favoriteList != null ? favoriteList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView busNumberBox, busName, busInfo;
        ImageView favIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            busNumberBox = itemView.findViewById(R.id.bus_number_box);
            busName = itemView.findViewById(R.id.bus_name);
            busInfo = itemView.findViewById(R.id.bus_info);
            favIcon = itemView.findViewById(R.id.fav_icon);
        }
    }
}

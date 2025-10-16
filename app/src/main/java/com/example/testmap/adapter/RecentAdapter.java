package com.example.testmap.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.model.RecentItem;

import java.util.List;

public class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.ViewHolder> {

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
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_bus, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecentItem item = recentList.get(position);
        holder.busNumber.setText(item.getBusNumber());
        holder.startStation.setText("승차: " + item.getStartStation());
        holder.endStation.setText("하차: " + item.getEndStation());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });

        holder.addFavIcon.setOnClickListener(v -> {
            if (listener != null) listener.onAddFavClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return Math.min(recentList.size(), 3);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView busNumber, startStation, endStation;
        ImageView addFavIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            busNumber = itemView.findViewById(R.id.bus_number_recent);
            startStation = itemView.findViewById(R.id.start_station);
            endStation = itemView.findViewById(R.id.end_station);
            addFavIcon = itemView.findViewById(R.id.add_to_fav);
        }
    }
}

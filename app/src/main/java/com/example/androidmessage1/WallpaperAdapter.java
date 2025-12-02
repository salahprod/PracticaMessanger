package com.example.androidmessage1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.WallpaperViewHolder> {

    private List<WallpaperItem> wallpaperItems;
    private OnWallpaperClickListener listener;
    private int selectedPosition = -1;

    public interface OnWallpaperClickListener {
        void onWallpaperClick(WallpaperItem wallpaperItem, int position);
        void onClearWallpaper();
    }

    public WallpaperAdapter(List<WallpaperItem> wallpaperItems, OnWallpaperClickListener listener) {
        this.wallpaperItems = wallpaperItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public WallpaperViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wallpaper, parent, false);
        return new WallpaperViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WallpaperViewHolder holder, int position) {
        WallpaperItem item = wallpaperItems.get(position);

        // Устанавливаем фон в зависимости от типа обоев
        holder.wallpaperView.setBackgroundResource(item.getDrawableResourceId());
        holder.wallpaperNameTv.setText(item.getName());

        // Показываем иконку выбора если элемент выбран
        if (position == selectedPosition) {
            holder.selectedIcon.setVisibility(View.VISIBLE);
        } else {
            holder.selectedIcon.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onWallpaperClick(item, position);
            }
            setSelectedPosition(position);
        });
    }

    @Override
    public int getItemCount() {
        return wallpaperItems.size();
    }

    public void setSelectedPosition(int position) {
        int previousPosition = selectedPosition;
        selectedPosition = position;
        if (previousPosition != -1) {
            notifyItemChanged(previousPosition);
        }
        if (selectedPosition != -1) {
            notifyItemChanged(selectedPosition);
        }
    }

    public void clearSelection() {
        if (selectedPosition != -1) {
            int positionToClear = selectedPosition;
            selectedPosition = -1;
            notifyItemChanged(positionToClear);
        }
    }

    public static class WallpaperViewHolder extends RecyclerView.ViewHolder {
        View wallpaperView;
        ImageView selectedIcon;
        TextView wallpaperNameTv;

        public WallpaperViewHolder(@NonNull View itemView) {
            super(itemView);
            wallpaperView = itemView.findViewById(R.id.wallpaper_view);
            selectedIcon = itemView.findViewById(R.id.selected_icon);
            wallpaperNameTv = itemView.findViewById(R.id.wallpaper_name_tv);
        }
    }
}
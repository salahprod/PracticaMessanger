package com.example.androidmessage1;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class SelectedFilesAdapter extends RecyclerView.Adapter<SelectedFilesAdapter.SelectedFileViewHolder> {

    private List<Uri> selectedFiles;
    private OnFileRemoveListener onFileRemoveListener;

    public interface OnFileRemoveListener {
        void onFileRemove(int position);
    }

    public SelectedFilesAdapter(List<Uri> selectedFiles, OnFileRemoveListener listener) {
        this.selectedFiles = selectedFiles;
        this.onFileRemoveListener = listener;
    }

    @NonNull
    @Override
    public SelectedFileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selected_file, parent, false);
        return new SelectedFileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectedFileViewHolder holder, int position) {
        Uri fileUri = selectedFiles.get(position);
        holder.bind(fileUri, position);
    }

    @Override
    public int getItemCount() {
        return selectedFiles.size();
    }

    class SelectedFileViewHolder extends RecyclerView.ViewHolder {
        ImageView filePreview;
        ImageView removeBtn;
        TextView fileName;

        public SelectedFileViewHolder(@NonNull View itemView) {
            super(itemView);
            filePreview = itemView.findViewById(R.id.fileThumbnail);
            removeBtn = itemView.findViewById(R.id.removeButton);
            fileName = itemView.findViewById(R.id.fileName);
        }

        public void bind(Uri fileUri, int position) {
            // Загружаем превью изображения
            Glide.with(itemView.getContext())
                    .load(fileUri)
                    .placeholder(R.drawable.artem)
                    .error(R.drawable.artem)
                    .override(200, 200)
                    .centerCrop()
                    .into(filePreview);

            // Получаем имя файла
            String name = getFileName(fileUri);
            fileName.setText(name);

            // Обработчик удаления файла
            removeBtn.setOnClickListener(v -> {
                if (onFileRemoveListener != null) {
                    onFileRemoveListener.onFileRemove(position);
                }
            });

            // Клик по превью для просмотра
            filePreview.setOnClickListener(v -> {
                // Открываем файл для просмотра
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, "image/*");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (intent.resolveActivity(itemView.getContext().getPackageManager()) != null) {
                    itemView.getContext().startActivity(intent);
                }
            });
        }

        private String getFileName(Uri uri) {
            String result = null;
            if (uri.getScheme().equals("content")) {
                try (android.database.Cursor cursor = itemView.getContext().getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (result == null) {
                result = uri.getLastPathSegment();
            }
            // Обрезаем длинные имена
            if (result != null && result.length() > 15) {
                result = result.substring(0, 12) + "...";
            }
            return result;
        }
    }
}
package com.zzr.bitmapdemo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {
    private List<String> urlList;
    private ImageLoader imageLoader;

    public MyAdapter(Context context, List<String> urlList) {
        this.urlList = urlList;
        imageLoader = ImageLoader.getInstance(context);
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_staggered_item, parent, false);
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_item, parent, false);
        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        String url = urlList.get(position);
        String tag = (String) holder.imageView.getTag();
        if (!url.equals(tag)) {
            holder.imageView.setImageResource(R.drawable.image_default);
        }

        holder.imageView.setTag(url);
        imageLoader.bindBitmap(url, holder.imageView);
    }

    @Override
    public int getItemCount() {
        return urlList == null ? 0 : urlList.size();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
//            imageView = itemView.findViewById(R.id.iv_image);
            imageView = itemView.findViewById(R.id.square_image);
        }
    }
}

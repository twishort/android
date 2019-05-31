package com.likeitstudio.adapter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Movie;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.likeitstudio.helper.Image;
import com.likeitstudio.twishort.DefaultApplication;
import com.likeitstudio.twishort.MainActivity;
import com.likeitstudio.twishort.R;

import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;

/**
 * Created on 31.05.17.
 */

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.ViewHolder> {

    public enum FileType {
        Image,
        Video,
        Gif
    }

    private static float thumbnailSize = 100;
    private int maxImagesCount = 4;
    private long minVideoDuration = 500;
    private long maxVideoDuration = 30000;
    private long maxFileSize = 15728640;

    private static ArrayList<File> list;
    private static Map<String, Bitmap> thumbnails = new HashMap<>();
    private static ArrayList<String> selected = new ArrayList<>();
    private static ArrayList<Integer> selectedPositions = new ArrayList<>();
    private static Map<String, Long> filesInfo = new HashMap<>();

    public int maxItemsToLoad;

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ImageView ivThumbnail;
        public TextView tvDuration;
        public RelativeLayout rlVideo;
        public TextView tvNumber;

        public String path;
        public long size;
        public long duration;
        public FileType type;
        public int position;

        public ViewHolder(View v) {
            super(v);
            ivThumbnail = (ImageView) v.findViewById(R.id.photo_image);
            tvDuration = (TextView) v.findViewById(R.id.video_duration);
            rlVideo = (RelativeLayout) v.findViewById(R.id.video_layout);
            tvNumber = (TextView) v.findViewById(R.id.video_number);
        }

        public void selectedState(boolean isSelected) {
            View parentView = (View) ivThumbnail.getParent();
            View parentParentView = (View) parentView.getParent();
            parentParentView.setBackgroundColor(isSelected ? ivThumbnail.getResources().getColor(R.color.action_bar) : 0x00000000);
            parentView.setAlpha(isSelected ? 0.4f : 1.0f);
            tvNumber.setText(isSelected && selected.size() > 1 ? "" + (selected.indexOf(path) + 1) : "");
        }

        public void selectedState() {
            selectedState(selected.contains(path));
        }
    }

    public PhotoAdapter(ArrayList<File> fileList) {
        list = fileList;
    }

    public ArrayList<File> getSelectedImages() {
        ArrayList<File> l = new ArrayList<>();
        for (Integer position : selectedPositions) {
            File file = list.get(position);
            if (getType(file.getAbsolutePath()) == FileType.Image) {
                l.add(file);
            }
        }
        return l;
    }

    public ArrayList<File> getSelectedVideos() {
        ArrayList<File> l = new ArrayList<>();
        for (Integer position : selectedPositions) {
            File file = list.get(position);
            if (getType(file.getAbsolutePath()) != FileType.Image) {
                l.add(file);
            }
        }
        return l;
    }

    public void longTap(ViewHolder holder) {}

    public void loadMore() {}

    private static FileType getType(String path) {
        String name = path.toLowerCase();
        if (name.endsWith(".gif")) {
            // Try to detect animated Gif
            return FileType.Gif;
        } else if (name.endsWith(".mp4")) {
            return FileType.Video;
        }
        return FileType.Image;
    }

    private void showAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(DefaultApplication.getCurrentActivity());
        alertDialog.setMessage(R.string.error_video);
        alertDialog.setIcon(R.mipmap.ic_launcher);
        alertDialog.setTitle(R.string.twitter_limit);
        alertDialog.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }

    @Override
    public PhotoAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.photo_item, parent, false);
        final ViewHolder holder = new ViewHolder(v);

        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isSelected = false;
                int index = selected.indexOf(holder.path);
                if (index >= 0) {
                    selected.remove(index);
                    selectedPositions.remove(index);
                } else {
                    if (!canSelectHolder(holder)) {
                        showAlert();
                        return;
                    }
                    if (selected.size() > 0) {
                        String path = selected.get(0);
                        FileType type = getType(path);
                        switch (holder.type) {
                            case Image:
                                if (type == FileType.Gif
                                        || type == FileType.Video) {
                                    // Remove all list cause 1 gif or 1 video permitted
                                    clearSelected(true);
                                }
                                if (selected.size() >= maxImagesCount) {
                                    // Remove old selected image if size >= max
                                    selected.remove(0);
                                    notifyItemChanged(selectedPositions.get(0));
                                    selectedPositions.remove(0);
                                }
                                break;
                            case Gif:
                            case Video:
                                // Remove all list cause 1 gif or 1 video permitted
                                clearSelected(true);
                        }
                        isSelected = true;
                    } else {
                        isSelected = true;
                    }
                }
                for (int position : selectedPositions) {
                    // Redraw selected items
                    notifyItemChanged(position);
                }
                if (isSelected) {
                    selected.add(holder.path);
                    selectedPositions.add(holder.position);
                }
                holder.selectedState(isSelected);
                return;
            }
        });

        v.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View view) {
                longTap(holder);
                return false;
            }
        });
        v.setLongClickable(true);

        return holder;
    }

    protected long videoDuration(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long duration = Long.parseLong( time );
        return duration;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        holder.path = list.get(position).getAbsolutePath();
        holder.type = getType(holder.path);
        holder.rlVideo.setVisibility(holder.type == FileType.Image ? View.GONE : View.VISIBLE);
        holder.selectedState();
        new Thread(new Runnable() {
            public void run() {
                final Bitmap bm;
                holder.position = position;
                holder.size = list.get(position).length();
                if (holder.type == FileType.Video) {
                    if (filesInfo.containsKey(holder.path)) {
                        holder.duration = filesInfo.get(holder.path);
                    } else {
                        holder.duration = videoDuration(holder.path);
                        filesInfo.put(holder.path, holder.duration);
                    }
                }
                String path = list.get(position).getAbsolutePath();
                if (!thumbnails.containsKey(path)) {
                    bm = getThumbnail(list.get(position));
                    thumbnails.put(path, bm);
                } else {
                    bm = thumbnails.get(path);
                }
                new Handler(DefaultApplication.getContext().getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        holder.ivThumbnail.setImageBitmap(bm);
                        if (holder.type == FileType.Video) {
                            long secondsDuration = (long) Math.ceil(holder.duration / 1000.0);
                            long minutes = (long) Math.floor(secondsDuration / 60.0);
                            long seconds = secondsDuration - minutes * 60;
                            holder.tvDuration.setText(holder.duration > 0 ? String.format("%d:%02d", minutes, seconds) : "");
                        } else if (holder.type == FileType.Gif) {
                            holder.tvDuration.setText("GIF");
                        }
                    }
                });
            }
        }).start();

        // Load more items
        if (position >= list.size() - 5
                && list.size() < maxItemsToLoad) {
            loadMore();
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    protected static Bitmap getThumbnail(File file) {
        Bitmap bm;
        String path = file.getAbsolutePath();
        switch (getType(path)) {
            case Image:
            case Gif:
                bm = Image.resampleFile(file, thumbnailSize);
                break;
            case Video:
            default:
                bm = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
                break;
        }
        return bm;
    }

    public static void refreshThumbnails() {
        new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < list.size(); i++) {
                    String path = list.get(i).getAbsolutePath();
                    if (!thumbnails.containsKey(path)) {
                        thumbnails.put(path, getThumbnail(list.get(i)));
                    }
                }
            }
        }).start();
    }

    public void setThumbnailSize(float size) {
        thumbnailSize = size;
    }

    public void setMaxImagesCount(int count) {
        maxImagesCount = count;
    }

    public void clearSelected(boolean notify) {
        while (selected.size() > 0) {
            selected.remove(0);
            int position = selectedPositions.get(0);
            if (notify) {
                notifyItemChanged(position);
            }
            selectedPositions.remove(0);
        }
    }

    private boolean canSelectHolder(ViewHolder holder) {
        switch (holder.type) {
            case Image:
                return true;
            case Video:
                return holder.duration >= minVideoDuration
                        && holder.duration <= maxVideoDuration
                        && holder.size <= maxFileSize;
            case Gif:
                return holder.size <= maxFileSize;
        }
        return true;
    }

    private boolean canSelectFile(File file) {
        String path = file.getAbsolutePath();
        FileType type = getType(path);
        switch (type) {
            case Image:
                return true;
            case Video:
                long duration;
                if (filesInfo.containsKey(path)) {
                    duration = filesInfo.get(path);
                } else {
                    duration = videoDuration(path);
                    filesInfo.put(path, duration);
                }
                return duration >= minVideoDuration
                        && duration <= maxVideoDuration
                        && file.length() <= maxFileSize;
            case Gif:
                return file.length() <= maxFileSize;
        }
        return true;
    }

    public void selectFirstNumber(int number) {
        clearSelected(true);
        for (int i = 0; i < Math.min(number, list.size()); i++) {
            File file = list.get(i);
            if (canSelectFile(file)) {
                selected.add(file.getAbsolutePath());
                selectedPositions.add(i);
            }
        }
    }

}

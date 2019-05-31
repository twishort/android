package com.likeitstudio.belt;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.likeitstudio.adapter.PhotoAdapter;
import com.likeitstudio.decoration.HorizontalSpaceItemDecoration;
import com.likeitstudio.helper.Bitmaps;
import com.likeitstudio.helper.Image;
import com.likeitstudio.twishort.DefaultApplication;
import com.likeitstudio.twishort.MainActivity;
import com.likeitstudio.twishort.R;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

/**
 * Created on 11.06.17.
 */

public class PhotoBelt {

    protected int IMAGES_SHOW_COUNT = 20;

    public LinearLayout llPhoto;
    public RecyclerView rvPhoto;
    public ImageButton ibCamera;
    public ImageView ivBackground;
    protected ArrayList<File> mediaList = new ArrayList<>();
    protected static ArrayList<File> extraMediaList = new ArrayList<>();
    protected ArrayList<File> sortedMediaList = new ArrayList<>();
    protected boolean isLoadingMore = false;
    PhotoAdapter adapter;

    public PhotoBelt() {
        llPhoto = (LinearLayout) DefaultApplication.getCurrentActivity().findViewById(R.id.photo_layout);
        rvPhoto = (RecyclerView) DefaultApplication.getCurrentActivity().findViewById(R.id.photo_list);
        ibCamera = (ImageButton) DefaultApplication.getCurrentActivity().findViewById(R.id.camera_button);
        ivBackground = (ImageView) DefaultApplication.getCurrentActivity().findViewById(R.id.background);

        try {
            if (DefaultApplication.isBackground()) {
                ivBackground.setImageBitmap(DefaultApplication.getBackground());
            }
        } catch (Exception e) {}

        rvPhoto.setLayoutManager(new LinearLayoutManager(DefaultApplication.getContext(), LinearLayoutManager.HORIZONTAL, false));
        adapter = new PhotoAdapter(mediaList) {
            @Override
            public void loadMore() {
                if (!isLoadingMore) {
                    loadMoreImages();
                    new Thread(new Runnable() {
                        public void run() {
                            refreshThumbnails();
                        }
                    }).start();
                }
            }

            @Override
            public void longTap(ViewHolder holder) {
                if (holder.type == FileType.Image) {
                    showSelectBackgroundDialog(holder.path);
                }
            }
        };
        adapter.setHasStableIds(true);
        rvPhoto.setAdapter(adapter);
        rvPhoto.setHasFixedSize(true);
        rvPhoto.setHorizontalScrollBarEnabled(true);
        rvPhoto.addItemDecoration(new HorizontalSpaceItemDecoration(1));
        rvPhoto.setItemAnimator(null); /*new DefaultItemAnimator()*/

        ibCamera.setOnClickListener(new View.OnClickListener()   {
            public void onClick(View v)  {
                showSelectMediaDialog();
            }
        });
    }

    public void addExtraMediaList(File file) {
        extraMediaList.add(file);
    }

    public void toggle() {
        if (llPhoto.getVisibility() == View.GONE) {
            show();
        } else {
            hide();
        }
    }

    public void show() {
        checkPermission();
        refresh();

        DefaultApplication.hideKeyboard();
        llPhoto.setTag(1);
        llPhoto.setVisibility(View.VISIBLE);
    }

    public void showIfNeeded() {
        if (llPhoto.getTag() != null) {
            llPhoto.setVisibility(View.VISIBLE);
        }
    }

    public void hide() {
        llPhoto.setTag(null);
        clear();
        llPhoto.setVisibility(View.GONE);
    }

    public void hideForInput() {
        llPhoto.setVisibility(View.GONE);
    }

    public void clear() {
        extraMediaList.clear();
        if (mediaList.size() > 0) {
            rvPhoto.scrollToPosition(0);
            adapter.clearSelected(false);
        }
    }

    public void refresh() {
        new Thread(new Runnable() {
            public void run() {
                readImageFiles();
                adapter.clearSelected(false);
                adapter.maxItemsToLoad = sortedMediaList.size();
                PhotoAdapter.refreshThumbnails();
                new Handler(DefaultApplication.getContext().getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        rvPhoto.scrollToPosition(0);
                        if (extraMediaList.size() > 0) {
                            adapter.selectFirstNumber(extraMediaList.size());
                        }
                        adapter.notifyDataSetChanged();
                    }
                }, 200);
            }
        }).start();
    }

    public ArrayList<Bitmap> getImages() {
        ArrayList<File> list = adapter.getSelectedImages();
        ArrayList<Bitmap> images = new ArrayList<>();
        for (File file : list) {
            images.add(Image.resampleFile(file, DefaultApplication.IMAGE_SIZE));
        }
        return images;
    }

    public File getVideo() {
        ArrayList<File> list = adapter.getSelectedVideos();
        return list.size() > 0 ? list.get(0) : null;
    }

    private void takeImage() {
        DefaultApplication.hideKeyboard();

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        DefaultApplication.getCurrentActivity().startActivityForResult(intent, DefaultApplication.REQUEST_CODE_IMAGE);
    }

    private void takeMedia(boolean isImage) {
        DefaultApplication.hideKeyboard();
        Intent intent = new Intent(isImage ? MediaStore.ACTION_IMAGE_CAPTURE : MediaStore.ACTION_VIDEO_CAPTURE);
        if (isImage) {
            /*
            File directory = (new ContextWrapper(DefaultApplication.getContext())).getDir(null, Context.MODE_PRIVATE);
            File file = new File(directory, "image.jpg");
            if (file.exists()) {
                file.delete();
            }
            intent.putExtra(MediaStore.EXTRA_OUTPUT, file.getAbsolutePath());*/
        } else {
            intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30);
            intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 15728640);
        }
        DefaultApplication.getCurrentActivity().startActivityForResult(intent,
                isImage ? DefaultApplication.REQUEST_CODE_PHOTO_IMAGE : DefaultApplication.REQUEST_CODE_PHOTO_VIDEO);
    }

    public void saveMedia(int requestCode, Intent data) {
        if (requestCode == DefaultApplication.REQUEST_CODE_PHOTO_IMAGE) {
            try {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
                File to = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera/" + timestamp + ".jpg");
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera");
                if (!dir.exists()) {
                    dir.mkdir();
                }

                /*
                File directory = (new ContextWrapper(DefaultApplication.getContext())).getDir(null, Context.MODE_PRIVATE);
                File file = new File(directory, "image.jpg");
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera");
                if (!dir.exists()) {
                    dir.mkdir();
                }
                if (file.exists()) {
                    file.renameTo(to);
                } else {*/

                    // Fix bug for some devices
                    File lastImage = getLastImage();
                    if (lastImage != null
                        && new Date().getTime() - lastImage.lastModified() < 10000) {
                        return;
                    }

                    Bitmap photo = (Bitmap) data.getExtras().get("data");
                    FileOutputStream fos;
                    fos = new FileOutputStream(to);
                    photo.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.close();
                //}
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setShowState(boolean show) {
        llPhoto.setTag(show ? 1 : null);
    }

    public boolean getShowState() {
        return llPhoto.getTag() != null ? true : false;
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("showPhoto", getShowState());
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState.getBoolean("showPhoto")) {
            show();
        }
    }

    // Permission

    public void checkPermission() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return;
        }
        int permissionCheck1 = DefaultApplication.getCurrentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        int permissionCheck2 = DefaultApplication.getCurrentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionCheck3 = DefaultApplication.getCurrentActivity().checkSelfPermission(Manifest.permission.CAMERA);
        if (permissionCheck1 != PackageManager.PERMISSION_GRANTED
                || permissionCheck2 != PackageManager.PERMISSION_GRANTED
                || permissionCheck3 != PackageManager.PERMISSION_GRANTED) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    hide();
                }
            }, 100);
            DefaultApplication.getCurrentActivity().requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            }, DefaultApplication.REQUEST_CODE_PERMISSION_PHOTO);
        }
    }

    // Read images

    protected void readImageFiles() {
        synchronized(sortedMediaList) {
            sortedMediaList.clear();
            ArrayList<String> exclude = new ArrayList<>();
            for (File file : extraMediaList) {
                exclude.add(file.getAbsolutePath());
            }
            ArrayList<File> files = new ArrayList<>();
            files.addAll(imageFiles(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), exclude));
            files.addAll(imageFiles(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), exclude));
            files.addAll(imageFiles(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), exclude));
            files.addAll(imageFiles(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), exclude));
            if (Build.VERSION.SDK_INT >= 19) {
                files.addAll(imageFiles(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), exclude));
            }
            Collections.sort(files, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
                }
            });
            sortedMediaList.addAll(extraMediaList);
            sortedMediaList.addAll(files);
            mediaList.clear();
            if (sortedMediaList.size() > 0) {
                for (int i = 0; i < Math.min(sortedMediaList.size(), IMAGES_SHOW_COUNT); i++) {
                    mediaList.add(sortedMediaList.get(i));
                }
            }
        }
    }

    protected File getLastImage() {
        ArrayList<File> images = imageFiles(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
        if (images.size() == 0) {
            return null;
        }
        Collections.sort(images, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
            }
        });
        return images.get(0);
    }

    protected void loadMoreImages() {
        isLoadingMore = true;
        try {
            int count = mediaList.size();
            if (count < sortedMediaList.size()) {
                int minCount = Math.min(count + IMAGES_SHOW_COUNT, sortedMediaList.size());
                for (int i = count; i < minCount; i++) {
                    mediaList.add(sortedMediaList.get(i));
                }
                adapter.notifyDataSetChanged();
            }
        } catch (Exception e) {}
        isLoadingMore = false;
    }

    protected ArrayList<File> imageFiles(File root) {
        return imageFiles(root, new ArrayList<String>());
    }

    protected ArrayList<File> imageFiles(File root, ArrayList<String> exclude) {
        ArrayList<File> a = new ArrayList<>();

        File[] files = root.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                //Log.e("!!!", files[i].getAbsolutePath());
                if (files[i].isDirectory()) {
                    if (!files[i].getName().equals(".thumbnails")) {
                        a.addAll(imageFiles(files[i], exclude));
                    }
                } else {
                    String name = files[i].getName().toLowerCase();
                    if ((name.endsWith(".jpg")
                            || name.endsWith(".mp4")
                            || name.endsWith(".png")
                            || name.endsWith(".gif"))
                            && !exclude.contains(files[i].getAbsolutePath())) {
                        a.add(files[i]);
                    }
                }
            }
        }
        return a;
    }

    protected void showSelectMediaDialog() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(DefaultApplication.getCurrentActivity());
        alertDialog.setTitle(R.string.select_media);
        alertDialog.setIcon(R.mipmap.ic_launcher);
        alertDialog.setPositiveButton(R.string.photo,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        takeMedia(true);
                    }
                });
        alertDialog.setNegativeButton(R.string.video,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        takeMedia(false);
                    }
                });
        alertDialog.show();
    }

    protected void showSelectBackgroundDialog(final String path) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(DefaultApplication.getCurrentActivity());
        alertDialog.setTitle(R.string.background);
        alertDialog.setMessage(R.string.set_background);
        alertDialog.setIcon(R.mipmap.ic_launcher);
        alertDialog.setPositiveButton(R.string.set,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        try {
                            ivBackground.setImageBitmap(BitmapFactory.decodeFile(path));
                            new Handler().post(new Runnable() {
                                @Override
                                public void run() {
                                    DefaultApplication.setBackground(new File(path));
                                }
                            });
                        } catch (Exception e) {
                            DefaultApplication.toast(R.string.error_background);
                        }
                    }
                });
        alertDialog.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        if (DefaultApplication.isBackground()) {
            alertDialog.setNeutralButton(R.string.remove,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            DefaultApplication.setBackground(null);
                            ivBackground.setImageBitmap(null);
                        }
                    });
        }
        alertDialog.show();
    }
}

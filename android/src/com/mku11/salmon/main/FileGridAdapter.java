package com.mku11.salmon.main;
/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/


import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.recyclerview.widget.RecyclerView;

import com.mku.android.salmonvault.R;
import com.mku11.salmon.image.Thumbnails;
import com.mku11.salmon.utils.Utils;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;


public class FileGridAdapter extends RecyclerView.Adapter<FileGridAdapter.ViewHolder> {
    private static final String TAG = FileGridAdapter.class.getName();
    private static final int MAX_CACHE_SIZE = 100 * 1024 * 1024;
    private static final int IMAGE_PROCESSING_THREADS = 4;
    private static final long VIDEO_THUMBNAIL_MSECS = 3000;
    private static final boolean displayItems = true;

    private final List<SalmonFile> items;
    private final LayoutInflater inflater;
    private final Function<Integer, Boolean> itemClicked;
    private final Activity activity;
    private int lastPositionPressed;
    private int cacheSize = 0;
    private final HashMap<String, Bitmap> cache = new HashMap<>();
    private ExecutorService executor;
    private ExecutorService filenameExecutor;

    // we use a deque and add jobs to the front for better user experience
    private final LinkedBlockingDeque<ThumbnailTask> thumbnailTasks = new LinkedBlockingDeque<>();
    private final List<ThumbnailTask> processingTasks = new ArrayList<>();

    public HashSet<SalmonFile> getSelectedFiles() {
        return selectedFiles;
    }

    private final HashSet<SalmonFile> selectedFiles = new HashSet<>();

    public void selectAll(boolean value) {
        if (value)
            selectedFiles.addAll(items);
        else
            selectedFiles.clear();
        notifyDataSetChanged();
    }

    public enum Mode {
        SINGLE_SELECT, MULTI_SELECT
    }

    private Mode mode = Mode.SINGLE_SELECT;

    public Mode getMode() {
        return mode;
    }

    public FileGridAdapter(Activity activity, List<SalmonFile> items, Function<Integer, Boolean> itemClicked) {
        this.items = items;
        this.inflater = LayoutInflater.from(activity);
        this.itemClicked = itemClicked;
        this.activity = activity;
        createExecutor();
    }

    public void setMultiSelect(boolean value) {
        selectedFiles.clear();
        mode = value ? Mode.MULTI_SELECT : Mode.SINGLE_SELECT;
        notifyDataSetChanged();
    }

    private void createExecutor() {
        executor = Executors.newFixedThreadPool(IMAGE_PROCESSING_THREADS);
        filenameExecutor = Executors.newFixedThreadPool(1);
    }

    public int getItemCount() {
        return items.size();
    }

    public void onBindViewHolder(@NonNull @NotNull ViewHolder holder, int position) {
        ViewHolder viewHolder = (ViewHolder) holder;
        SalmonFile salmonFile = null;

        try {
            if (mode == Mode.MULTI_SELECT) {
                viewHolder.selected.setVisibility(View.VISIBLE);
            } else {
                viewHolder.selected.setVisibility(View.GONE);
            }
            salmonFile = items.get(position);
            updateSelected(viewHolder, salmonFile);
            updateBackgroundColor(viewHolder);
            viewHolder.filename.setText("");
            viewHolder.extension.setText("");
            viewHolder.filesize.setText("");
            if (salmonFile.isDirectory()) {
                viewHolder.thumbnail.setColorFilter(null);
                viewHolder.thumbnail.setImageResource(R.drawable.folder);
            } else {
                viewHolder.thumbnail.setImageBitmap(null);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // queue filename, items, and thumbnail retrieval
        queueFileInfo(salmonFile, viewHolder, position);
    }

    private void updateSelected(ViewHolder viewHolder, SalmonFile salmonFile) {
        viewHolder.selected.setChecked(selectedFiles.contains(salmonFile));
    }

    private void queueFileInfo(SalmonFile salmonFile, ViewHolder viewHolder, int position) {
        filenameExecutor.submit(() -> {
            long size = 0;
            String tag;
            String items = "";
            try {
                tag = salmonFile.getRealPath();
                String filename = salmonFile.getBaseName();
                if (salmonFile.isDirectory() && displayItems)
                    items = salmonFile.listFiles().length + " " + activity.getString(R.string.Items);
                else
                    size = salmonFile.getSize();
                String finalItems = items;
                long finalSize = size;
                activity.runOnUiThread(() -> {
                    updateFileInfo(viewHolder, filename, finalItems, tag, salmonFile, finalSize);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void updateFileInfo(ViewHolder viewHolder, String filename, String items, String tag, SalmonFile salmonFile, long size) {
        viewHolder.filename.setText(filename);
        if (salmonFile.isDirectory()) {
            viewHolder.filesize.setText(items);
            viewHolder.extension.setText("");
            viewHolder.thumbnail.setColorFilter(null);
            viewHolder.thumbnail.setImageResource(R.drawable.folder);
        } else {
            viewHolder.thumbnail.setTag(tag);
            viewHolder.thumbnail.setImageBitmap(null);
            viewHolder.extension.setText("");
            viewHolder.filesize.setText(Utils.getBytes(size, 2));
            String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(filename).toLowerCase();
            updateFileIcon(viewHolder, ext);
            if (updateIconFromCache(viewHolder, tag, ext))
                return;

            thumbnailTasks.addFirst(new ThumbnailTask(viewHolder, salmonFile, tag));
            while (thumbnailTasks.size() > 50)
                thumbnailTasks.removeLast();
            executor.submit(() -> {
                try {
                    if (thumbnailTasks.size() > 0) {
                        ThumbnailTask task = thumbnailTasks.removeFirst();
                        updateThumbnail(task);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }
    }

    private boolean updateIconFromCache(ViewHolder viewHolder, String tag, String ext) {
        if (cache.containsKey(tag)) {
            Bitmap bitmap = cache.get(tag);
            if (bitmap == null)
                updateFileIcon(viewHolder, ext);
            else {
                updateThumbnailIcon(viewHolder, cache.get(tag));
            }
            return true;
        }
        return false;
    }

    private void updateThumbnailIcon(ViewHolder viewHolder, Bitmap bitmap) {
        viewHolder.thumbnail.setImageBitmap(bitmap);
        viewHolder.thumbnail.setColorFilter(null);
        viewHolder.extension.setVisibility(View.GONE);
        viewHolder.extension.setText("");
    }

    private void updateFileIcon(ViewHolder viewHolder, String extension) {
        viewHolder.thumbnail.setImageResource(R.drawable.file);
        int extColor;
        try {
            extColor = getFileColorFromExtension(extension);
            viewHolder.thumbnail.setColorFilter(extColor, PorterDuff.Mode.MULTIPLY);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        viewHolder.extension.setVisibility(View.VISIBLE);
        viewHolder.extension.setText(extension);
    }

    private int getFileColorFromExtension(String extension) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = extension.getBytes(Charset.defaultCharset());
        byte[] hashValue = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hashValue.length; i++)
            sb.append(String.format("%02x", hashValue[i]));
        return Color.parseColor("#" + sb.substring(0, 6));
    }

    private void updateThumbnail(ThumbnailTask task) throws Exception {
        processingTasks.add(task);
        Bitmap bitmap = null;
        String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(task.salmonFile.getBaseName()).toLowerCase();
        try {
            bitmap = getFileThumbnail(task.salmonFile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Bitmap finalBitmap = bitmap;
        activity.runOnUiThread(() -> {
            try {
                String ctag = (String) task.viewHolder.thumbnail.getTag();
                if (ctag == null) {
                    return;
                }
                if (finalBitmap == null) {
                    updateFileIcon(task.viewHolder, ext);
                } else if (ctag.equals(task.tag)) {
                    Animation animation = AnimationUtils.loadAnimation(activity, R.anim.thumbnail);
                    task.viewHolder.thumbnail.startAnimation(animation);
                    updateThumbnailIcon(task.viewHolder, finalBitmap);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                processingTasks.remove(task);
            }
        });
    }

    public void resetCache(RecyclerView view) {
        cacheSize = 0;
        cache.clear();
        executor.shutdownNow();
        filenameExecutor.shutdownNow();
        thumbnailTasks.clear();
        for (ThumbnailTask task : processingTasks) {
            task.tag = null;
        }
        processingTasks.clear();
        view.getRecycledViewPool().clear();
        view.setRecycledViewPool(new RecyclerView.RecycledViewPool());
        createExecutor();
    }

    private Bitmap getFileThumbnail(SalmonFile salmonFile) throws Exception {
        String path = salmonFile.getRealPath();
        Bitmap bitmap = null;
        String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
        if (ext.equals("mp4")) {
            bitmap = Thumbnails.getVideoThumbnail(salmonFile, VIDEO_THUMBNAIL_MSECS);
        } else if (ext.equals("png") || ext.equals("jpg") || ext.equals("bmp") || ext.equals("webp") || ext.equals("gif")) {
            bitmap = Thumbnails.getImageThumbnail(salmonFile);
        }
        checkCacheSize();
        addBitmapToCache(path, bitmap);
        return bitmap;
    }

    private void addBitmapToCache(String path, Bitmap bitmap) {
        cache.put(path, bitmap);
        if (bitmap != null)
            cacheSize += bitmap.getAllocationByteCount();
    }

    private void checkCacheSize() {
        if (cacheSize > MAX_CACHE_SIZE) {
            // ResetCache();
        }
    }

    @NonNull
    public FileGridAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.file_item, parent, false);
        return new ViewHolder(view, itemClicked, this);
    }

    public int getPosition() {
        return lastPositionPressed;
    }

    private class ThumbnailTask extends LinkedList {
        public final ViewHolder viewHolder;
        public final SalmonFile salmonFile;
        public String tag;

        public ThumbnailTask(ViewHolder viewHolder, SalmonFile salmonFile, String tag) {
            this.viewHolder = viewHolder;
            this.salmonFile = salmonFile;
            this.tag = tag;
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView thumbnail;
        public TextView filename;
        public TextView filesize;
        public TextView extension;
        public CheckBox selected;

        public ViewHolder(View itemView, Function<Integer, Boolean> itemClicked, FileGridAdapter adapter) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            filename = itemView.findViewById(R.id.filename);
            filesize = itemView.findViewById(R.id.filesize);
            extension = itemView.findViewById(R.id.extension);
            selected = itemView.findViewById(R.id.selected);

            itemView.setOnClickListener((View view) ->
            {
                if (mode == Mode.MULTI_SELECT) {
                    selected.setChecked(!selected.isChecked());
                    SalmonFile salmonFile = items.get(super.getLayoutPosition());
                    if (selected.isChecked())
                        selectedFiles.add(salmonFile);
                    else selectedFiles.remove(salmonFile);
                    updateBackgroundColor(this);
                } else if (itemClicked == null || !itemClicked.apply(super.getLayoutPosition())) {
                    adapter.lastPositionPressed = super.getLayoutPosition();
                    itemView.showContextMenu();
                }
            });

            itemView.setOnLongClickListener((View view) -> {
                adapter.lastPositionPressed = super.getLayoutPosition();
                itemView.showContextMenu();
                return true;
            });
        }
    }

    private void updateBackgroundColor(ViewHolder viewHolder) {
        if (viewHolder.selected.isChecked())
            viewHolder.itemView.setBackgroundColor(activity.getColor(R.color.colorPrimaryDark));
        else
            viewHolder.itemView.setBackgroundColor(0);
    }
}

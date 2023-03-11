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
import com.mku11.salmon.BitConverter;
import com.mku11.salmon.image.Thumbnails;
import com.mku11.salmon.utils.Utils;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;


public class FileAdapter extends RecyclerView.Adapter	 {
    private static final String TAG = FileAdapter.class.getName();
    private static final int MAX_CACHE_SIZE = 100 * 1024 * 1024;
    private static final long VIDEO_THUMBNAIL_MSECS = 3000;
    private final boolean displayItems = true;

    private final List<SalmonFile> items;
    private final LayoutInflater inflater;
    private final Function<Integer, Boolean> itemClicked;
    private final Activity activity;
    private final HashMap<SalmonFile, Bitmap> bitmapCache = new HashMap<>();
    private final HashSet<SalmonFile> processed = new HashSet<>();
    // we use a deque and add jobs to the front for better user experience
    private final LinkedBlockingDeque<ViewHolder> tasks = new LinkedBlockingDeque<>();
    private int lastPositionPressed;
    private int cacheSize = 0;
    private boolean isMultiSelect = false;
    private HashSet<SalmonFile> selectedFiles = new HashSet<>();
    private SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/YYYY");
    private Mode mode = Mode.SINGLE_SELECT;
    private ExecutorService executor;

    public FileAdapter(Activity activity, List<SalmonFile> items, Function<Integer, Boolean> itemClicked) {
        this.items = items;
        this.inflater = LayoutInflater.from(activity);
        this.itemClicked = itemClicked;
        this.activity = activity;
        createThread();
    }

    public HashSet<SalmonFile> getSelectedFiles() {
        return selectedFiles;
    }

    public void selectAll(boolean value) {
        if (value)
            selectedFiles.addAll(items);
        else
            selectedFiles.clear();
        notifyDataSetChanged();
    }

    public Mode getMode() {
        return mode;
    }

    public void setMultiSelect(boolean value) {
        selectedFiles.clear();
        mode = value ? Mode.MULTI_SELECT : Mode.SINGLE_SELECT;
        notifyDataSetChanged();
    }

    public void stop() {
        tasks.clear();
        executor.shutdownNow();
    }

    private void createThread() {
        executor = Executors.newFixedThreadPool(4);
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ViewHolder viewHolder = (ViewHolder) holder;
        try {
            if (mode == Mode.MULTI_SELECT) {
                viewHolder.selected.setVisibility(View.VISIBLE);
            } else {
                viewHolder.selected.setVisibility(View.GONE);
            }
            viewHolder.salmonFile = items.get(position);
            updateSelected(viewHolder, viewHolder.salmonFile);
            updateBackgroundColor(viewHolder);
            viewHolder.filename.setText("");
            viewHolder.extension.setText("");
            viewHolder.filesize.setText("");
            viewHolder.filedate.setText("");
            if (viewHolder.salmonFile.isDirectory()) {
                viewHolder.thumbnail.setColorFilter(null);
                viewHolder.thumbnail.setImageResource(R.drawable.folder);
            } else {
                viewHolder.thumbnail.setImageBitmap(null);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (processed.contains(viewHolder.salmonFile)) {
            retrieveFileInfo(viewHolder);
        } else {
            tasks.remove(viewHolder);
            tasks.addFirst(viewHolder);
            executor.submit(() -> {
                try {
                    ViewHolder task = tasks.take();
                    retrieveFileInfo(task);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void updateSelected(ViewHolder viewHolder, SalmonFile salmonFile) {
        viewHolder.selected.setChecked(selectedFiles.contains(salmonFile));
    }

    private void retrieveFileInfo(ViewHolder viewHolder) {
        long size = 0;
        long date;
        String items = "";
        SalmonFile file = viewHolder.salmonFile;
        try {
            String filename = viewHolder.salmonFile.getBaseName();
            activity.runOnUiThread(() -> {
                viewHolder.filename.setText(filename);
            });
            if (viewHolder.salmonFile.isDirectory() && displayItems)
                items = viewHolder.salmonFile.listFiles().length + " " + activity.getString(R.string.Items);
            else
                size = viewHolder.salmonFile.getSize();
            date = viewHolder.salmonFile.getLastDateTimeModified();

            String finalItems = items;
            long finalSize = size;
            long finalDate = date;
            if (file != viewHolder.salmonFile)
                return;
            activity.runOnUiThread(() -> {
                updateFileInfo(viewHolder, filename, finalItems, viewHolder.salmonFile, finalSize, finalDate);
            });

            String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(filename).toLowerCase();
            if (bitmapCache.containsKey(file)) {
                activity.runOnUiThread(() -> {
                    updateIconFromCache(viewHolder, file, ext);
                });
            } else if (viewHolder.salmonFile.isFile()) {
                Bitmap bitmap = null;
                try {
                    bitmap = getFileThumbnail(viewHolder.salmonFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Bitmap finalBitmap = bitmap;
                if (file != viewHolder.salmonFile)
                    return;
                activity.runOnUiThread(() -> {
                    if (finalBitmap == null) {
                        updateFileIcon(viewHolder, ext);
                    } else {
                        Animation animation = AnimationUtils.loadAnimation(activity, R.anim.thumbnail);
                        viewHolder.thumbnail.startAnimation(animation);
                        updateThumbnailIcon(viewHolder, finalBitmap);
                    }
                });
            }
            processed.add(viewHolder.salmonFile);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void updateFileInfo(ViewHolder viewHolder, String filename,
                                String items, SalmonFile salmonFile,
                                long size, long date) {
        viewHolder.filename.setText(filename);
        viewHolder.filedate.setText(formatter.format(new Date(date)));
        if (salmonFile.isDirectory()) {
            viewHolder.filesize.setText(items);
            viewHolder.extension.setText("");
            viewHolder.thumbnail.setColorFilter(null);
            viewHolder.thumbnail.setImageResource(R.drawable.folder);
        } else {
            viewHolder.thumbnail.setImageBitmap(null);
            viewHolder.extension.setText("");
            viewHolder.filesize.setText(Utils.getBytes(size, 2));
        }
    }

    private boolean updateIconFromCache(ViewHolder viewHolder, SalmonFile file, String ext) {
        if (bitmapCache.containsKey(file)) {
            Bitmap bitmap = bitmapCache.get(file);
            if (bitmap == null)
                updateFileIcon(viewHolder, ext);
            else {
                updateThumbnailIcon(viewHolder, bitmapCache.get(file));
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
        sb.append(BitConverter.toHex(hashValue));
        return Color.parseColor("#" + sb.substring(0, 6));
    }

    public void resetCache(RecyclerView view) {
        cacheSize = 0;
        bitmapCache.clear();
        tasks.clear();
		processed.clear();
        view.getRecycledViewPool().clear();
        view.setRecycledViewPool(new RecyclerView.RecycledViewPool());
    }

    private Bitmap getFileThumbnail(SalmonFile salmonFile) throws Exception {
        Bitmap bitmap = null;
        String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
        if (ext.equals("mp4")) {
            bitmap = Thumbnails.getVideoThumbnail(salmonFile, VIDEO_THUMBNAIL_MSECS);
        } else if (ext.equals("png") || ext.equals("jpg") || ext.equals("bmp") || ext.equals("webp") || ext.equals("gif")) {
            bitmap = Thumbnails.getImageThumbnail(salmonFile);
        }
        checkCacheSize();
        addBitmapToCache(salmonFile, bitmap);
        return bitmap;
    }

    private void addBitmapToCache(SalmonFile file, Bitmap bitmap) {
        bitmapCache.put(file, bitmap);
        if (bitmap != null)
            cacheSize += bitmap.getAllocationByteCount();
    }

    private void checkCacheSize() {
        if (cacheSize > MAX_CACHE_SIZE) {
            // ResetCache();
        }
    }

    @NonNull
    public FileAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.file_item, parent, false);
        return new ViewHolder(view, itemClicked, this);
    }

    public int getPosition() {
        return lastPositionPressed;
    }

    private void updateBackgroundColor(ViewHolder viewHolder) {
        if (viewHolder.selected.isChecked())
            viewHolder.itemView.setBackgroundColor(activity.getColor(R.color.colorPrimaryDark));
        else
            viewHolder.itemView.setBackgroundColor(0);
    }

    public enum Mode {
        SINGLE_SELECT, MULTI_SELECT
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView thumbnail;
        public TextView filename;
        public TextView filesize;
        public TextView filedate;
        public TextView extension;
        public CheckBox selected;
        public SalmonFile salmonFile;

        public ViewHolder(View itemView, Function<Integer, Boolean> itemClicked, FileAdapter adapter) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            filename = itemView.findViewById(R.id.filename);
            filesize = itemView.findViewById(R.id.filesize);
            filedate = itemView.findViewById(R.id.filedate);
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

}

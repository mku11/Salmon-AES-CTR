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
using Android.App;
using Android.Graphics;
using Android.Views;
using Android.Widget;
using AndroidX.RecyclerView.Widget;
using Salmon.Droid.Utils;
using Salmon.Droid.Image;
using Salmon.FS;
using Java.Lang;
using Java.Util.Concurrent;
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using Android.Views.Animations;
using System.Security.Cryptography;
using Java.Util;
using Java.Text;
using Java.Security;
using Android.OS;

namespace Salmon.Droid.Main
{
    class FileAdapter : RecyclerView.Adapter
    {
        private static readonly string TAG = typeof(FileAdapter).Name;
        private const int MAX_CACHE_SIZE = 100 * 1024 * 1024;
        private const long VIDEO_THUMBNAIL_MSECS = 3000;

        private bool displayItems = true;
        private List<SalmonFile> items;
        private LayoutInflater inflater;
        private Func<int, bool> itemClicked;
        private Activity activity;
        private int lastPositionPressed;
        private int cacheSize = 0;
        private bool isMultiSelect = false;
        private HashSet<SalmonFile> selectedFiles = new HashSet<SalmonFile>();
        private ConcurrentDictionary<SalmonFile, Bitmap> bitmapCache = new ConcurrentDictionary<SalmonFile, Bitmap>();
        private HashSet<SalmonFile> processed = new HashSet<SalmonFile>();
        private Mode mode = Mode.SINGLE_SELECT;
        private IExecutorService executor;
        private SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/YYYY");
        // we use a deque and add jobs to the front for better user experience
        private LinkedBlockingDeque tasks = new LinkedBlockingDeque();


        public FileAdapter(Activity activity, List<SalmonFile> items, Func<int, bool> itemClicked)
        {
            this.items = items;
            this.inflater = LayoutInflater.From(activity);
            this.itemClicked = itemClicked;
            this.activity = activity;
            CreateThread();
        }

        public HashSet<SalmonFile> GetSelectedFiles()
        {
            return selectedFiles;
        }

        public void SelectAll(bool value)
        {
            if (value)
            {
                foreach (SalmonFile item in items)
                    selectedFiles.Add(item);
            }
            else
                selectedFiles.Clear();
            NotifyDataSetChanged();
        }

        public Mode GetMode()
        {
            return mode;
        }

        public void SetMultiSelect(bool value)
        {
            selectedFiles.Clear();
            mode = value ? Mode.MULTI_SELECT : Mode.SINGLE_SELECT;
            NotifyDataSetChanged();
        }

        public void Stop()
        {
            tasks.Clear();
            executor.ShutdownNow();
        }

        private void CreateThread()
        {
            executor = Executors.NewFixedThreadPool(4);
        }

        public override int ItemCount => items.Count;

        public override void OnBindViewHolder(RecyclerView.ViewHolder holder, int position)
        {
            ViewHolder viewHolder = (ViewHolder)holder;
            try
            {
                if (mode == Mode.MULTI_SELECT)
                {
                    viewHolder.selected.Visibility = ViewStates.Visible;
                }
                else
                {
                    viewHolder.selected.Visibility = ViewStates.Gone;
                }
                viewHolder.salmonFile = items[position];
                UpdateSelected(viewHolder, viewHolder.salmonFile);
                UpdateBackgroundColor(viewHolder);
                viewHolder.filename.Text = "";
                viewHolder.extension.Text = "";
                viewHolder.filesize.Text = "";
                viewHolder.filedate.Text = "";
                if (viewHolder.salmonFile.IsDirectory())
                {
                    viewHolder.thumbnail.SetColorFilter(null);
                    viewHolder.thumbnail.SetImageResource(Resource.Drawable.folder);
                }
                else
                {
                    viewHolder.thumbnail.SetImageBitmap(null);
                }
            }
            catch (System.Exception ex)
            {
                ex.PrintStackTrace();
            }
            if (processed.Contains(viewHolder.salmonFile))
            {
                RetrieveFileInfo(viewHolder);
            }
            else
            {
                tasks.Remove(viewHolder);
                tasks.AddFirst(viewHolder);
                executor.Submit(new Runnable(() =>
                {
                    try
                    {
                        ViewHolder task = (ViewHolder)tasks.Take();
                        RetrieveFileInfo(task);
                    }
                    catch (InterruptedException e)
                    {
                        e.PrintStackTrace();
                    }
                }));
            }
        }

        private void UpdateSelected(ViewHolder viewHolder, SalmonFile salmonFile)
        {
            viewHolder.selected.Checked = selectedFiles.Contains(salmonFile);
        }

        private void RetrieveFileInfo(ViewHolder viewHolder)
        {
            long size = 0;
            long date;
            string items = "";
            SalmonFile file = viewHolder.salmonFile;
            try
            {
                string filename = viewHolder.salmonFile.GetBaseName();
                activity.RunOnUiThread(() =>
                {
                    viewHolder.filename.Text = filename;
                });
                if (viewHolder.salmonFile.IsDirectory() && displayItems)
                    items = viewHolder.salmonFile.ListFiles().Length + " " + activity.GetString(Resource.String.Items);
                else
                    size = viewHolder.salmonFile.GetSize();
                date = viewHolder.salmonFile.GetLastDateTimeModified();

                string finalItems = items;
                long finalSize = size;
                long finalDate = date;
                if (file != viewHolder.salmonFile)
                    return;
                activity.RunOnUiThread(() =>
                {
                    updateFileInfo(viewHolder, filename, finalItems, viewHolder.salmonFile, finalSize, finalDate);
                });

                string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(filename).ToLower();
                if (bitmapCache.ContainsKey(file))
                {
                    activity.RunOnUiThread(() =>
                    {
                        UpdateIconFromCache(viewHolder, file, ext);
                    });
                }
                else if (viewHolder.salmonFile.IsFile())
                {
                    Bitmap bitmap = null;
                    try
                    {
                        bitmap = GetFileThumbnail(viewHolder.salmonFile);
                    }
                    catch (System.Exception e)
                    {
                        e.PrintStackTrace();
                    }
                    Bitmap finalBitmap = bitmap;
                    if (file != viewHolder.salmonFile)
                        return;
                    activity.RunOnUiThread(() =>
                    {
                        if (finalBitmap == null)
                        {
                            UpdateFileIcon(viewHolder, ext);
                        }
                        else
                        {
                            Animation animation = AnimationUtils.LoadAnimation(activity, Resource.Animation.thumbnail);
                            viewHolder.thumbnail.StartAnimation(animation);
                            UpdateThumbnailIcon(viewHolder, finalBitmap);
                        }
                    });
                }
                processed.Add(viewHolder.salmonFile);
            }
            catch (System.Exception ex)
            {
                ex.PrintStackTrace();
            }
        }

        private void updateFileInfo(ViewHolder viewHolder, string filename,
                                    string items, SalmonFile salmonFile,
                                    long size, long date)
        {
            viewHolder.filename.Text = filename;
            viewHolder.filedate.Text = formatter.Format(new Date(date));
            if (salmonFile.IsDirectory())
            {
                viewHolder.filesize.Text = items;
                viewHolder.extension.Text = "";
                viewHolder.thumbnail.SetColorFilter(null);
                viewHolder.thumbnail.SetImageResource(Resource.Drawable.folder);
            }
            else
            {
                viewHolder.thumbnail.SetImageBitmap(null);
                viewHolder.extension.Text = "";
                viewHolder.filesize.Text = WindowUtils.GetBytes(size, 2);
            }
        }

        private bool UpdateIconFromCache(ViewHolder viewHolder, SalmonFile file, string ext)
        {
            if (bitmapCache.ContainsKey(file))
            {
                Bitmap bitmap = bitmapCache[file];
                if (bitmap == null)
                    UpdateFileIcon(viewHolder, ext);
                else
                {
                    UpdateThumbnailIcon(viewHolder, bitmapCache[file]);
                }
                return true;
            }
            return false;
        }

        private void UpdateThumbnailIcon(ViewHolder viewHolder, Bitmap bitmap)
        {
            viewHolder.thumbnail.SetImageBitmap(bitmap);
            viewHolder.thumbnail.SetColorFilter(null);
            viewHolder.extension.Visibility = ViewStates.Gone;
            viewHolder.extension.Text = "";
        }

        private void UpdateFileIcon(ViewHolder viewHolder, string extension)
        {
            viewHolder.thumbnail.SetImageResource(Resource.Drawable.file);
            int extColor;
            try
            {
                extColor = GetFileColorFromExtension(extension);
                viewHolder.thumbnail.SetColorFilter(new Color(extColor), PorterDuff.Mode.Multiply);
            }
            catch (NoSuchAlgorithmException e)
            {
                e.PrintStackTrace();
            }
            viewHolder.extension.Visibility = ViewStates.Visible;
            viewHolder.extension.Text = extension;
        }

        private int GetFileColorFromExtension(string extension)
        {
            MessageDigest md = MessageDigest.GetInstance("MD5");
            byte[] bytes = System.Text.Encoding.UTF8.GetBytes(extension);
            byte[] hashValue = md.Digest(bytes);
            StringBuilder sb = new StringBuilder();
            sb.Append(BitConverter.ToHex(hashValue));
            return Color.ParseColor("#" + sb.Substring(0, 6));
        }

        public void ResetCache(RecyclerView view)
        {
            cacheSize = 0;
            bitmapCache.Clear();
            tasks.Clear();
            processed.Clear();
            view.GetRecycledViewPool().Clear();
            view.SetRecycledViewPool(new RecyclerView.RecycledViewPool());
        }

        private Bitmap GetFileThumbnail(SalmonFile salmonFile)
        {
            Bitmap bitmap = null;
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(salmonFile.GetBaseName()).ToLower();
            if (ext.Equals("mp4"))
            {
                bitmap = Thumbnails.GetVideoThumbnail(salmonFile, VIDEO_THUMBNAIL_MSECS);
            }
            else if (ext.Equals("png") || ext.Equals("jpg") || ext.Equals("bmp") || ext.Equals("webp") || ext.Equals("gif"))
            {
                bitmap = Thumbnails.GetImageThumbnail(salmonFile);
            }
            CheckCacheSize();
            AddBitmapToCache(salmonFile, bitmap);
            return bitmap;
        }

        private void AddBitmapToCache(SalmonFile file, Bitmap bitmap)
        {
            bitmapCache[file] = bitmap;
            if (bitmap != null)
                cacheSize += bitmap.AllocationByteCount;
        }

        private void CheckCacheSize()
        {
            if (cacheSize > MAX_CACHE_SIZE)
            {
                // ResetCache();
            }
        }


        public override RecyclerView.ViewHolder OnCreateViewHolder(ViewGroup parent, int viewType)
        {
            View view = inflater.Inflate(Resource.Layout.file_item, parent, false);
            return new ViewHolder(view, itemClicked, this);
        }

        public int GetPosition()
        {
            return lastPositionPressed;
        }

        private void UpdateBackgroundColor(ViewHolder viewHolder)
        {
            if (viewHolder.selected.Checked)
                viewHolder.ItemView.SetBackgroundColor(new Color(activity.GetColor(Resource.Color.colorPrimaryDark)));
            else
                viewHolder.ItemView.SetBackgroundColor(new Color(0));
        }

        public enum Mode
        {
            SINGLE_SELECT, MULTI_SELECT
        }

        public class ViewHolder : RecyclerView.ViewHolder
        {
            private FileAdapter adapter;
            public ImageView thumbnail;
            public TextView filename;
            public TextView filesize;
            public TextView filedate;
            public TextView extension;
            public CheckBox selected;
            public SalmonFile salmonFile;

            public ViewHolder(View itemView, Func<int, bool> itemClicked, FileAdapter adapter) : base(itemView)
            {
                this.adapter = adapter;
                thumbnail = (ImageView)itemView.FindViewById(Resource.Id.thumbnail);
                filename = (TextView)itemView.FindViewById(Resource.Id.filename);
                filesize = (TextView)itemView.FindViewById(Resource.Id.filesize);
                filedate = (TextView)itemView.FindViewById(Resource.Id.filedate);
                extension = (TextView)itemView.FindViewById(Resource.Id.extension);
                selected = (CheckBox)itemView.FindViewById(Resource.Id.selected);

                itemView.Click += (object sender, EventArgs e) =>
                {
                    if (adapter.mode == Mode.MULTI_SELECT)
                    {
                        selected.Checked = !selected.Checked;
                        SalmonFile salmonFile = adapter.items[base.LayoutPosition];
                        if (selected.Checked)
                            adapter.selectedFiles.Add(salmonFile);
                        else adapter.selectedFiles.Remove(salmonFile);
                        adapter.UpdateBackgroundColor(this);
                    }
                    else if (itemClicked == null || !itemClicked.Invoke(base.LayoutPosition))
                    {
                        adapter.lastPositionPressed = base.LayoutPosition;
                        itemView.ShowContextMenu();
                    }
                };

                itemView.LongClick += (object sender, View.LongClickEventArgs e) =>
                {
                    adapter.lastPositionPressed = base.LayoutPosition;
                    itemView.ShowContextMenu();
                };
            }
        }

    }
}
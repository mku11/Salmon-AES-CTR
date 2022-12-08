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

namespace Salmon.Droid.Main
{
    class FileGridAdapter : RecyclerView.Adapter
    {
        private static readonly string TAG = typeof(FileGridAdapter).Name;
        private const int MAX_CACHE_SIZE = 100 * 1024 * 1024;
        private const int IMAGE_PROCESSING_THREADS = 4;

        private List<SalmonFile> items;
        private LayoutInflater inflater;
        private Func<int, bool> itemClicked;
        private Activity activity;
        private int lastPositionPressed;
        private int cacheSize = 0;
        private ConcurrentDictionary<string, Bitmap> cache = new ConcurrentDictionary<string, Bitmap>();
        private IExecutorService executor = Executors.NewFixedThreadPool(IMAGE_PROCESSING_THREADS);
        // we use a deque and always add jobs to the front so the user scrolls we should process the most recent set of images
        private LinkedBlockingDeque thumbnailTasks = new LinkedBlockingDeque();

        private class ThumbnailTask : LinkedList
        {
            public ViewHolder viewHolder;
            public SalmonFile salmonFile;
            public string tag;

            public ThumbnailTask(ViewHolder viewHolder, SalmonFile salmonFile, string tag)
            {
                this.viewHolder = viewHolder;
                this.salmonFile = salmonFile;
                this.tag = tag;
            }
        }

        public FileGridAdapter(Activity activity, List<SalmonFile> items, Func<int, bool> itemClicked)
        {
            this.items = items;
            this.inflater = LayoutInflater.From(activity);
            this.itemClicked = itemClicked;
            this.activity = activity;
        }

        public override int ItemCount => items.Count;

        public override void OnBindViewHolder(RecyclerView.ViewHolder holder, int position)
        {
            ViewHolder viewHolder = holder as ViewHolder;
            SalmonFile salmonFile = null;

            string tag = null;
            try
            {
                salmonFile = items[position];
                tag = salmonFile.GetRealPath();
                string filename = salmonFile.GetBaseName();
                viewHolder.filename.Text = filename;
                long size = salmonFile.GetSize();
                viewHolder.filesize.Text = GetBytes(size, 2);
                string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(filename).ToLower();
                viewHolder.thumbnail.Tag = tag;
                viewHolder.thumbnail.SetImageBitmap(null);
                viewHolder.extension.Text = "";
                UpdateFileIcon(viewHolder, ext);
                if (UpdateIconFromCache(viewHolder, tag, ext))
                    return;
            }
            catch (System.Exception ex)
            {
                ex.PrintStackTrace();
                return;
            }

            
            thumbnailTasks.AddFirst(new ThumbnailTask(viewHolder, salmonFile, tag));
            while (thumbnailTasks.Size() > 15)
                thumbnailTasks.RemoveLast();
            executor.Submit(new Runnable(() =>
            {
                try
                {
                    if (thumbnailTasks.Size() > 0)
                    {
                        ThumbnailTask task = (ThumbnailTask)thumbnailTasks.RemoveFirst();
                        UpdateThumbnail(task.viewHolder, task.salmonFile, task.tag);
                    }
                }
                catch (System.Exception ex)
                {
                    ex.PrintStackTrace();
                }
            }));
        }

        private bool UpdateIconFromCache(ViewHolder viewHolder, string tag, string ext)
        {
            if (cache.ContainsKey(tag))
            {
                Bitmap bitmap = cache[tag];
                if (bitmap == null)
                    UpdateFileIcon(viewHolder, ext);
                else
                {
                    UpdateThumbnailIcon(viewHolder, cache[tag]);
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
            Color extColor = GetFileColorFromExtension(extension);
            viewHolder.thumbnail.SetColorFilter(extColor, PorterDuff.Mode.Multiply);
            viewHolder.extension.Visibility = ViewStates.Visible;
            viewHolder.extension.Text = extension;
        }

        private Color GetFileColorFromExtension(string extension)
        {
            MD5 hmac = MD5.Create();
            byte[] bytes = System.Text.UTF8Encoding.UTF8.GetBytes(extension);
            byte[] hashValue = hmac.ComputeHash(bytes);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hashValue.Length; i++)
                sb.Append(hashValue[i].ToString("X2"));
            return Color.ParseColor("#" + sb.ToString().Substring(0, 6));
        }

        private void UpdateThumbnail(ViewHolder viewHolder, SalmonFile salmonFile, string tag)
        {
            Bitmap bitmap = null;
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(salmonFile.GetBaseName()).ToLower();
            try
            {
                bitmap = GetFileThumbnail(salmonFile);
            }
            catch (System.Exception e)
            {
                e.PrintStackTrace();
            }

            activity.RunOnUiThread(new Runnable(() =>
            {
                try
                {
                    string ctag = (string)viewHolder.thumbnail.Tag;
                    
                    if (bitmap == null)
                    {
                        UpdateFileIcon(viewHolder, ext);
                    }
                    else if (ctag.Equals(tag))
                    {
                        Animation animation = AnimationUtils.LoadAnimation(activity, Resource.Animation.thumbnail);
                        viewHolder.thumbnail.StartAnimation(animation);
                        UpdateThumbnailIcon(viewHolder, bitmap);
                    }
                }
                catch (System.Exception ex)
                {
                    ex.PrintStackTrace();
                }
            }));
        }

        public void ResetCache()
        {
            cacheSize = 0;
            cache.Clear();
        }

        private Bitmap GetFileThumbnail(SalmonFile salmonFile)
        {
            string path = salmonFile.GetRealPath();
            Bitmap bitmap = null;
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(salmonFile.GetBaseName()).ToLower();
            if (ext.Equals("mp4"))
            {
                bitmap = Thumbnails.GetVideoThumbnail(salmonFile);
            }
            else if (ext.Equals("png") || ext.Equals("jpg") || ext.Equals("bmp") || ext.Equals("webp") || ext.Equals("gif"))
            {
                bitmap = Thumbnails.GetImageThumbnail(salmonFile);
            }
            CheckCacheSize();
            AddBitmapToCache(path, bitmap);
            return bitmap;
        }

        private void AddBitmapToCache(string path, Bitmap bitmap)
        {
            cache[path] = bitmap;
            if(bitmap!=null)
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

        public class ViewHolder : RecyclerView.ViewHolder
        {
            private FileGridAdapter adapter;
            public ImageView thumbnail;
            public TextView filename;
            public TextView filesize;
            public TextView extension;

            public ViewHolder(View itemView, Func<int, bool> itemClicked, FileGridAdapter adapter) : base(itemView)
            {
                this.adapter = adapter;
                thumbnail = (ImageView)itemView.FindViewById(Resource.Id.thumbnail);
                filename = (TextView)itemView.FindViewById(Resource.Id.filename);
                filesize = (TextView)itemView.FindViewById(Resource.Id.filesize);
                extension = (TextView)itemView.FindViewById(Resource.Id.extension);

                itemView.Click += (object sender, EventArgs e) =>
                {
                    if (itemClicked == null || !itemClicked.Invoke(base.LayoutPosition))
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

        public int GetPosition()
        {
            return lastPositionPressed;
        }

        public static string GetBytes(long bytes, int decimals)
        {
            string format = "#";
            if (decimals > 0)
                format += ".";
            for (int i = 0; i < decimals; i++)
            {
                format += "0";
            }
            NumberFormat formatter = new DecimalFormat(format);

            if (bytes > 1024 * 1024 * 1024)
            {
                return formatter.Format(((double)bytes) / (1024 * 1024 * 1024)) + " GB";
            }
            else if (bytes > 1024 * 1024)
            {
                return formatter.Format(((double)bytes) / (1024 * 1024)) + " MB";
            }
            else if (bytes > 1024)
            {
                return formatter.Format(((double)bytes) / 1024) + " KB";
            }
            else if (bytes >= 0)
                return ("" + bytes + " B");
            return "";

        }
    }
}
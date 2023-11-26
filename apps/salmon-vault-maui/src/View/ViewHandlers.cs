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

#if ANDROID
using Android.Views;
using AndroidX.Core.View;
using Microsoft.Maui;
#endif

using Microsoft.Maui;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Controls.Handlers.Items;
using Microsoft.Maui.Handlers;
using Microsoft.Maui.Platform;
using System;
#if IOS
using UIKit;
#endif
using Items = Microsoft.Maui.Controls.Handlers.Items;

namespace Salmon.Vault.View;

public class ViewHandlers
{
    public event EventHandler<ContextMenuEventArgs> OnContextMenu;
    public class ContextMenuEventArgs : EventArgs
    {
        public object Context;
        public int Index = -1;
    }

    public ViewHandlers()
    {
        BorderHandler.Mapper.AppendToMapping("BorderHandlers", CreateBorderHandler);
        ImageHandler.Mapper.AppendToMapping("ImageHandlers", CreateImageHandler);
    }

    public void CreateBorderHandler(IBorderHandler handler, Microsoft.Maui.IBorderView border)
    {
#if WINDOWS
			handler.PlatformView.Holding += PlatformView_Holding;  
        handler.PlatformView.IsRightTapEnabled = true;
        handler.PlatformView.RightTapped +=  PlatformView_RightClick;
#endif
#if ANDROID
        handler.PlatformView.LongClick += PlatformView_LongClick;
#endif
#if IOS
			handler.PlatformView.UserInteractionEnabled = true;  
			handler.PlatformView.AddGestureRecognizer(new UILongPressGestureRecognizer(HandleLongClick));  
#endif
    }

    public void CreateImageHandler(IImageHandler handler, Microsoft.Maui.IImage image)
    {
#if WINDOWS
			handler.PlatformView.Holding += PlatformView_Holding;  
        handler.PlatformView.IsRightTapEnabled = true;
        handler.PlatformView.RightTapped +=  PlatformView_RightClick;
#endif
#if ANDROID
        handler.PlatformView.LongClick += PlatformView_LongClick;
#endif
#if IOS
			handler.PlatformView.UserInteractionEnabled = true;  
			handler.PlatformView.AddGestureRecognizer(new UILongPressGestureRecognizer(HandleLongClick));  
#endif
    }

#if WINDOWS
    private void PlatformView_Holding(object sender, Microsoft.UI.Xaml.Input.HoldingRoutedEventArgs e)
    {
        OnContextMenuWindows(sender);
    }
    private void PlatformView_RightClick(object sender, Microsoft.UI.Xaml.Input.RightTappedRoutedEventArgs e)
    {
        OnContextMenuWindows(sender);
    }

    private void OnContextMenuWindows(object sender)
    {
        Microsoft.Maui.Platform.ContentPanel contentPanel = sender as Microsoft.Maui.Platform.ContentPanel;
        if(contentPanel == null)
            return;
        Microsoft.Maui.Platform.WrapperView wrapperView = contentPanel.Parent as Microsoft.Maui.Platform.WrapperView;
        if(wrapperView == null)
            return;
        Microsoft.Maui.Controls.Platform.ItemContentControl itemContentControl = wrapperView.Parent as Microsoft.Maui.Controls.Platform.ItemContentControl;
        if(itemContentControl == null)
            return;
        OnContextMenu(sender, new ContextMenuEventArgs() { Context = itemContentControl.FormsDataContext});
    }
#endif
#if IOS
    private void HandleLongClick(UILongPressGestureRecognizer sender)  
    {  
		    OnContextMenu(sender, null);
    }  
#endif
#if ANDROID
    [Obsolete]
    private void PlatformView_LongClick(object sender, Android.Views.View.LongClickEventArgs e)
    {
        Android.Views.View view = sender as Android.Views.View;
        ItemContentView itemContentView = null;
        while (view is Android.Views.View)
        {
            if (view is ItemContentView)
            {
                itemContentView = view as ItemContentView;
                break;
            }
            view = (Android.Views.View)view.Parent;
        }
        if (itemContentView == null)
            return;
        Items.MauiRecyclerView<ReorderableItemsView,Items.GroupableItemsViewAdapter<
                ReorderableItemsView,Items.IGroupableItemsViewSource>, Items.IGroupableItemsViewSource>
            recyclerView = (Items.MauiRecyclerView<ReorderableItemsView,
            Items.GroupableItemsViewAdapter<ReorderableItemsView,
            Items.IGroupableItemsViewSource>, Items.IGroupableItemsViewSource>)itemContentView.Parent;
        if (recyclerView == null)
            return;
        int index = -1;
        for (int i = 0; i < recyclerView.ChildCount; i++)
        {
            AndroidX.RecyclerView.Widget.RecyclerView.ViewHolder viewHolder = recyclerView.FindViewHolderForAdapterPosition(i);
            ItemContentView itemView = (ItemContentView)viewHolder.ItemView;
            if (itemView == itemContentView)
            {
                index = i;
                break;
            }
        }
        OnContextMenu(sender, new ContextMenuEventArgs() { Index = index });
    }
#endif
}

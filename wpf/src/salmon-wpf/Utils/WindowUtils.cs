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
using System;
using System.Threading;
using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;

namespace Salmon.Window
{
    public class WindowUtils
    {
        private static string iconPath;
        public static void SetDefaultIconPath(string iconPath)
        {
            WindowUtils.iconPath = iconPath;
        }
        public static void RunOnMainThread(Action action )
        {
            Application.Current.Dispatcher.Invoke(action);
        }

        public static void RunOnMainThread(System.Action action, int delay)
        {
            new Thread(() =>
            {
                Thread.Sleep(delay);
                RunOnMainThread(action);
            }).Start();
        }
        public static void SetChildBackground(DependencyObject obj, Action<DependencyObject> OnViewFound)
        {
            if (OnViewFound != null)
                OnViewFound.Invoke(obj);
            if (obj.GetType() == typeof(Popup))
                SetChildBackground(((Popup)obj).Child, OnViewFound);
            if (obj.GetType() == typeof(Border))
                SetChildBackground(((Border)obj).Child, OnViewFound);
            if (obj.GetType() == typeof(Path) && ((Path)obj).ContextMenu != null)
                SetChildBackground((DependencyObject)((Path)obj).ContextMenu, OnViewFound);
            if (obj.GetType() == typeof(ScrollViewer))
                SetChildBackground((DependencyObject)((ScrollViewer)obj).Content, OnViewFound);
            int k = VisualTreeHelper.GetChildrenCount(obj);
            for (int i = 0; i < k; i++)
            {
                DependencyObject child = VisualTreeHelper.GetChild(obj, i);
                SetChildBackground(child, OnViewFound);
            }
        }
    }
}
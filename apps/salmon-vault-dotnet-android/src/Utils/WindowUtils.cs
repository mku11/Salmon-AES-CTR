﻿/*
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
using Android.Content;
using System;
using System.Threading.Tasks;

namespace Salmon.Vault.Utils;

public class WindowUtils
{
    public static Activity UiActivity { get; set; }

    public static void RunOnMainThread(Action action)
    {
        UiActivity.RunOnUiThread(action);
    }

    public static void RunOnMainThread(Action action, int delay)
    {
        Task.Delay(delay).ContinueWith((task) =>
        {
            RunOnMainThread(action);
        });
    }

    public static void RemoveFromRecents(Activity activity, bool value)
    {
        ActivityManager am = (ActivityManager)activity.GetSystemService(Context.ActivityService);
        if (am != null)
        {
            var tasks = am.AppTasks;
            if (tasks != null && tasks.Count > 0)
            {
                tasks[0].SetExcludeFromRecents(value);
            }
        }
    }

}
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

using Salmon.Vault.Extensions;
using Salmon.Vault.Utils;
using System;
using System.Collections.Concurrent;
using System.Threading;

namespace Salmon.Vault.Model;

public static class SalmonFileAttrQueue
{

    public class AttrTask
    {
        public Func<object> Getter;
        public Action<object> Setter;

        public AttrTask(Func<object> Getter, Action<object> Setter)
        {
            this.Getter = Getter;
            this.Setter = Setter;
        }
    }

    private static BlockingCollection<AttrTask> tasks = new BlockingCollection<AttrTask>();
    public static void UpdatePropertyAsync<T>(Func<T> Getter, Action<T> Setter)
    {
        QueueTask(Getter, Setter);
    }

    private static void QueueTask<T>(Func<T> Getter, Action<T> Setter)
    {
        AttrTask nTask = new AttrTask(() => Getter(), (obj) => Setter((T) obj));
        tasks.Add(nTask);
        ThreadPool.QueueUserWorkItem((state) =>
        {
            try
            {
                AttrTask task = tasks.Take();
                object obj = task.Getter();
                WindowUtils.RunOnMainThread(() => task.Setter(obj));
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
            }
        });
    }
}
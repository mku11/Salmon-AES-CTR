package com.mku.salmon.vault.model;
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

import com.mku.func.Consumer;
import com.mku.func.Function;
import com.mku.func.Supplier;
import com.mku.salmon.vault.utils.WindowUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class SalmonFileAttrQueue
{
    public static class AttrTask
    {
        public Function<Void,Object> getter;
        public Consumer<Object> setter;

        public AttrTask(Function<Void,Object> getter, Consumer<Object> setter)
        {
            this.getter = getter;
            this.setter = setter;
        }
    }
    private static final Executor executor = Executors.newFixedThreadPool(2);
    private static LinkedBlockingQueue<AttrTask> tasks = new LinkedBlockingQueue<>();
    public static <T> void UpdatePropertyAsync(Supplier<T> getter, Consumer<T> setter)
    {
        QueueTask(getter, setter);
    }

    private static <T> void QueueTask(Supplier<T> getter, Consumer<T> setter)
    {
        AttrTask nTask = new AttrTask((unused) -> getter.get(), (obj) -> setter.accept((T) obj));
        tasks.add(nTask);
        executor.execute(() ->
        {
            try
            {
                AttrTask task = tasks.take();
                Object obj = task.getter.apply(null);
                WindowUtils.runOnMainThread(() -> task.setter.accept(obj));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
    }
}
using Android.Graphics;
using Android.Util;
using Android.Views;
using System;
using Context = Android.Content.Context;

namespace Salmon.Droid.Main
{
    public class PieProgress : View
    {
        private int _progress;

        public PieProgress(Context context) : base(context)
        {
        }
        public PieProgress(Context context, IAttributeSet attrs) : base(context, attrs)
        {

        }

        public PieProgress(Context context, IAttributeSet attrs, int defStyleAttr) : base(context, attrs, defStyleAttr)
        {

        }

        protected override void OnDraw(Canvas canvas)
        {
            //XXX: rebuild the project if you modify this file
            base.OnDraw(canvas);
            canvas.DrawColor(Color.Transparent);

            RectF outer = new RectF(0, 0, Width, Height);
            Paint paint = new Paint(PaintFlags.AntiAlias);

            paint.SetStyle(Paint.Style.Fill);
            paint.Color = Color.ParseColor("#222222");
            canvas.DrawOval(outer, paint);

            paint.Color = Color.Cyan;
            canvas.DrawArc(outer, 270, _progress * 3.6f, true, paint);

            paint.SetStyle(Paint.Style.Fill);
            paint.Color = Color.ParseColor("#222222");
            RectF inner = new RectF(Width / 6, Height / 6, Width * 5 / 6, Height * 5 / 6);
            canvas.DrawOval(inner, paint);

            paint.Color = Color.White;
            paint.TextSize = 24;
            paint.FakeBoldText = true;
            paint.TextAlign = Paint.Align.Center;
            canvas.DrawText(_progress + " %", Width / 2, Height / 2, paint);
        }

        public void SetProgress(int value)
        {
            _progress = value;
            Invalidate();
        }
    }
}
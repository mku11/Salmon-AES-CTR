package com.mku11.salmon.main;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class PieProgress extends View {
    private int _progress;

    public PieProgress(Context context) {
        super(context);
    }

    public PieProgress(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PieProgress(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT);

        RectF outer = new RectF(0, 0, getWidth(), getHeight());
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#222222"));
        canvas.drawOval(outer, paint);

        paint.setColor(Color.CYAN);
        canvas.drawArc(outer, 270, _progress * 3.6f, true, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#222222"));
        RectF inner = new RectF(getWidth()/6, getHeight()/6, getWidth()*5/6, getHeight()*5/6);
        canvas.drawOval(inner, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(24);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(_progress + " %",
                getWidth()/2, getHeight()/2, paint);
    }

    public void setProgress(int value) {
        _progress = value;
        invalidate();
    }

}
package com.zach.backscreenprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.Locale;

/** 主屏上的传感器实时读数条，用来确认体感是否真的在出数。 */
public class TiltView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float ax, ay, az, gx, gy, gz;

    public TiltView(Context c) {
        super(c);
        p.setStyle(Paint.Style.FILL);
        t.setColor(0xFFE8E8F0);
        t.setTextSize(22f);
    }

    public void update(float ax, float ay, float az, float gx, float gy, float gz) {
        this.ax = ax; this.ay = ay; this.az = az;
        this.gx = gx; this.gy = gy; this.gz = gz;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF0E0E14);
        float[] vals = {ax, ay, az};
        float[] gvals = {gx, gy, gz};
        String[] names = {"ax", "ay", "az"};
        String[] gnames = {"gx", "gy", "gz"};

        float h = c.getHeight() / 6f;
        for (int i = 0; i < 3; i++) {
            bar(c, i * h, h, names[i], vals[i], 12f, 0xFF4FF0C0);
        }
        for (int i = 0; i < 3; i++) {
            bar(c, (i + 3) * h, h, gnames[i], gvals[i], 12f, 0xFFFFD54A);
        }
        // 倾斜角
        double pitch = Math.toDegrees(Math.atan2(-az, Math.sqrt(ax * ax + ay * ay)));
        double roll = Math.toDegrees(Math.atan2(ax, Math.sqrt(ay * ay + az * az)));
        t.setTextSize(22f);
        c.drawText(String.format(Locale.US, "pitch %.1f°   roll %.1f°", pitch, roll), 12, h * 6 - 6, t);
    }

    private void bar(Canvas c, float top, float h, String name, float v, float scale, int color) {
        float cy = top + h / 2f;
        t.setTextSize(20f);
        t.setColor(0xFF8A8A99);
        c.drawText(name, 8, cy + 7, t);
        float x0 = 70, w = c.getWidth() - x0 - 90;
        p.setColor(0xFF1A1A22);
        c.drawRect(x0, cy - 6, x0 + w, cy + 6, p);
        float u = Math.max(-1f, Math.min(1f, v / scale));
        p.setColor(color);
        float cx = x0 + w / 2f;
        c.drawRect(Math.min(cx, cx + u * w / 2f), cy - 6, Math.max(cx, cx + u * w / 2f), cy + 6, p);
        t.setColor(0xFFE8E8F0);
        c.drawText(String.format(Locale.US, "%.2f", v), x0 + w + 8, cy + 7, t);
    }
}

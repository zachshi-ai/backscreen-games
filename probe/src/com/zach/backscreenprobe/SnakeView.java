package com.zach.backscreenprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/**
 * 体感贪吃蛇：不需要任何按键，机身前后倾角控制所在轨道。
 * 同一套 View 既能挂在背屏 Presentation 上，也能退化到主屏。
 */
public class SnakeView extends View {

    public interface TiltSource { float getAx(); float getAy(); float getAz(); }

    private static final int COLS = 20, ROWS = 12;

    private TiltSource src;
    private final ArrayList<int[]> segs = new ArrayList<int[]>();
    private final ArrayList<int[]> obs = new ArrayList<int[]>();
    private int[] food;
    private int targetRow = 6;
    private float vrow = 6f, acc = 0f, flash = 0f, lowU = 0f, speed = 6f;
    private int growing = 0, score = 0;
    private boolean over = false, flip = false;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            tick(16);
            invalidate();
            h.postDelayed(this, 16);
        }
    };

    public SnakeView(Context c) {
        super(c);
        p.setStyle(Paint.Style.FILL);
        tx.setColor(0xFFE8E8F0);
        tx.setTextSize(46f);
        reset();
    }

    public void setTiltSource(TiltSource s) { this.src = s; }

    private void reset() {
        segs.clear();
        segs.add(new int[]{2, 6});
        segs.add(new int[]{1, 6});
        segs.add(new int[]{0, 6});
        obs.clear();
        score = 0; growing = 0; acc = 0; speed = 6f; over = false;
        targetRow = 6; vrow = 6f;
        food = randCell();
    }

    private boolean free(int col, int row) {
        if (food != null && food[0] == col && food[1] == row) return false;
        for (int[] s : segs) if (s[0] == col && s[1] == row) return false;
        for (int[] o : obs) if (o[0] == col && o[1] == row) return false;
        return true;
    }

    private int[] randCell() {
        for (int i = 0; i < 300; i++) {
            int c = (int) (Math.random() * COLS), w = (int) (Math.random() * ROWS);
            if (free(c, w)) return new int[]{c, w};
        }
        return new int[]{COLS - 1, ROWS - 1};
    }

    private void step() {
        int[] head = segs.get(segs.size() - 1);
        int nc = (head[0] + 1) % COLS, nr = targetRow;
        if (food != null && food[0] == nc && food[1] == nr) {
            growing += 1; score += 1; flash = 1f;
            speed = Math.min(14f, 6f + score * 0.35f);
            food = randCell();
            if (score >= 5 && obs.size() < Math.min(5, score / 5)) {
                int[] c = randCell();
                obs.add(c);
            }
        }
        for (int i = 0; i < obs.size(); i++) {
            int[] o = obs.get(i);
            if (o[0] == nc && o[1] == nr) {
                obs.remove(i);
                for (int k = 0; k < 3 && segs.size() > 1; k++) segs.remove(0);
                flash = 1f;
                if (segs.size() < 2) { over = true; return; }
                break;
            }
        }
        segs.add(new int[]{nc, nr});
        if (growing > 0) growing--; else segs.remove(0);
    }

    private void tick(int ms) {
        if (over) { flash = Math.max(0, flash - 0.05f); return; }
        if (src != null) {
            double ax = src.getAx(), ay = src.getAy(), az = src.getAz();
            double pitch = Math.toDegrees(Math.atan2(-az, Math.sqrt(ax * ax + ay * ay)));
            float u = (float) Math.max(-1, Math.min(1, pitch / 28.0));
            if (flip) u = -u;
            lowU = lowU * 0.82f + u * 0.18f;
            float eff = Math.abs(lowU) < 0.12f ? 0f : lowU;
            targetRow = Math.round((eff + 1f) / 2f * (ROWS - 1));
            targetRow = Math.max(0, Math.min(ROWS - 1, targetRow));
        }
        vrow += (targetRow - vrow) * 0.25f;
        float dt = ms / 1000f;
        acc += dt * speed;
        int guard = 0;
        while (acc >= 1f && guard++ < 4) { acc -= 1f; step(); if (over) break; }
        flash = Math.max(0, flash - 0.06f);
    }

    @Override
    protected void onAttachedToWindow() { super.onAttachedToWindow(); h.post(loop); }

    @Override
    protected void onDetachedFromWindow() { super.onDetachedFromWindow(); h.removeCallbacks(loop); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            if (over) reset(); else flip = !flip;   // 点一下翻转方向，兼容不同握持姿势
            invalidate();
            return true;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF08080C);
        float cw = getWidth() / (float) COLS, ch = getHeight() / (float) ROWS;

        if (food != null) {
            p.setColor(0xFFFFD54A);
            c.drawCircle(food[0] * cw + cw / 2, food[1] * ch + ch / 2, Math.min(cw, ch) * 0.22f, p);
        }
        p.setColor(0xFFFF4D5E);
        for (int[] o : obs) {
            r.set(o[0] * cw + 4, o[1] * ch + 4, o[0] * cw + cw - 4, o[1] * ch + ch - 4);
            c.drawRoundRect(r, 8, 8, p);
        }
        for (int i = 0; i < segs.size(); i++) {
            int[] s = segs.get(i);
            float x = s[0] * cw + acc * cw;
            if (x > getWidth()) x -= getWidth();
            float y = s[1] * ch;
            if (i == segs.size() - 1) y += (vrow - s[1]) * ch;
            boolean head = (i == segs.size() - 1);
            p.setColor(head ? 0xFF4FF0C0 : 0xFF22B890);
            r.set(x + 3, y + 3, x + cw - 3, y + ch - 3);
            c.drawRoundRect(r, 8, 8, p);
        }
        tx.setTextSize(Math.max(28f, getWidth() * 0.035f));
        c.drawText(String.valueOf(score), 24, 52, tx);
        tx.setTextSize(Math.max(18f, getWidth() * 0.018f));
        tx.setColor(0xFF8A8A99);
        c.drawText("pitch驱动 · 点一下反向 · " + String.format(java.util.Locale.US, "%.1f", speed) + " 格/秒", 24, 84, tx);

        if (flash > 0) {
            p.setColor(((int) (flash * 34) << 24) | 0xFFFFFF);
            c.drawRect(0, 0, getWidth(), getHeight(), p);
        }
        if (over) {
            p.setColor(0xCC04040A);
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            tx.setColor(0xFFF2F2F8);
            tx.setTextSize(getWidth() * 0.06f);
            c.drawText("撞碎了", getWidth() / 2f - tx.measureText("撞碎了") / 2f, getHeight() / 2f - 10, tx);
            tx.setTextSize(getWidth() * 0.03f);
            tx.setColor(0xFF8A8A99);
            c.drawText("得分 " + score + " · 点一下重开", getWidth() / 2f - tx.measureText("得分 " + score + " · 点一下重开") / 2f,
                    getHeight() / 2f + 40, tx);
        }
    }
}

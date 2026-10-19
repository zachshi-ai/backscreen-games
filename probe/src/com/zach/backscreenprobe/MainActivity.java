package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 定向试探 IStepOSDisplayManager 的 void 方法（replySize=0 的 5 个 code），
 * 用几种常见帧编码依次下发，看背屏点阵是否有反应。
 */
public class MainActivity extends Activity {

    private static final String SVC = "display_ext";
    private static final String DESC = "android.hardware.display.IStepOSDisplayManager";
    private static final int[] CODES = {4, 5, 11, 12, 40};

    private TextView logView;
    private TextView statusView;
    private String report = "";
    private IBinder binder;
    private volatile boolean running = false;
    private int seq = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080C);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("点阵定向试探");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(18);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextColor(0xFFFFD54A);
        statusView.setTextSize(14);
        statusView.setPadding(0, dp(6), 0, dp(6));
        statusView.setText("待开始");
        root.addView(statusView);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(10.5f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button run = new Button(this);
        run.setText("开始扫描（盯着背屏看）");
        run.setAllCaps(false);
        run.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        });
        root.addView(run);

        TextView hint = new TextView(this);
        hint.setText("每发一帧之前会震动一下；序列号同步显示在上方。若背屏有任何闪动，记住大概第几次。");
        hint.setTextColor(0xFF6D6D7A);
        hint.setTextSize(11f);
        root.addView(hint);

        setContentView(root);
        bind();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void bind() {
        try {
            Class<?> smc = Class.forName("android.os.ServiceManager");
            Method get = smc.getMethod("getService", String.class);
            Object o = get.invoke(null, SVC);
            if (o instanceof IBinder) binder = (IBinder) o;
            append("binder: " + (binder == null ? "null" : "OK"));
        } catch (Throwable t) {
            append("bind 失败: " + t);
        }
    }

    private void start() {
        if (running) return;
        if (binder == null) { bind(); if (binder == null) return; }
        running = true;
        new Thread(new Runnable() {
            @Override public void run() {
                for (int code : CODES) {
                    for (int variant = 1; variant <= 5; variant++) {
                        if (!running) break;
                        seq++;
                        final int s = seq, c = code, v = variant;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                statusView.setText("#" + s + "  code=" + c + "  变体" + v);
                            }
                        });
                        buzz(30);
                        String r = send(code, variant);
                        append("\n#" + s + " code=" + c + " 变体" + v + " -> " + r);
                        dump();
                        try { Thread.sleep(700); } catch (InterruptedException ignored) { }
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() { statusView.setText("扫描完成"); }
                });
                running = false;
            }
        }).start();
    }

    private String send(int code, int variant) {
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(DESC);
            byte[] on32 = new byte[128];
            for (int i = 0; i < on32.length; i++) on32[i] = (byte) 0xFF;
            byte[] on64 = new byte[512];
            for (int i = 0; i < on64.length; i++) on64[i] = (byte) 0xFF;
            switch (variant) {
                case 1: d.writeInt(32); d.writeInt(32); d.writeByteArray(on32); break;
                case 2: d.writeByteArray(on32); break;
                case 3: d.writeInt(0); break;
                case 4: d.writeInt(64); d.writeInt(32); d.writeByteArray(on64); break;
                case 5: d.writeInt(32); d.writeInt(32); d.writeInt(0); break;
                default: break;
            }
            boolean ok = binder.transact(code, d, r, 0);
            return "ok=" + ok + " reply=" + r.dataSize();
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            return c.getClass().getSimpleName() + ":" + c.getMessage();
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    private void buzz(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Throwable ignored) { }
    }

    private void append(String s) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        report = report + "[" + ts + "] " + s + "\n";
        runOnUiThread(new Runnable() {
            @Override public void run() { logView.setText(report); }
        });
        dump();
    }

    private void dump() {
        try {
            byte[] bytes = report.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "dotmatrix_scan.txt");
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                    if (os != null) { os.write(bytes); os.flush(); os.close(); }
                }
            }
        } catch (Throwable ignored) { }
    }
}

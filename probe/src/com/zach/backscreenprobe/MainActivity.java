package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
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
 * 真正推帧：对已确认吞下 (int 宽, int 高, byte[] 位图) 的 code 5 / 11 下发
 * 全亮-全灭交替图案，覆盖多种分辨率，配合已开启的背屏观察。
 */
public class MainActivity extends Activity {

    private static final String SVC = "display_ext";
    private static final String DESC = "android.hardware.display.IStepOSDisplayManager";
    private static final int[] SIZES = {8, 16, 24, 32, 40, 48, 64};

    private TextView logView;
    private TextView statusView;
    private String report = "";
    private IBinder binder;
    private volatile boolean running = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080C);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("点阵推帧测试");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(18);
        root.addView(title);

        TextView warn = new TextView(this);
        warn.setText("先确认背屏已经点亮（设置里打开背屏 / 点亮一次），再点下面的按钮。");
        warn.setTextColor(0xFFFFD54A);
        warn.setTextSize(12.5f);
        warn.setPadding(0, dp(6), 0, dp(2));
        root.addView(warn);

        statusView = new TextView(this);
        statusView.setTextColor(0xFFFFD54A);
        statusView.setTextSize(14);
        statusView.setText("待开始");
        root.addView(statusView);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(10f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button run = new Button(this);
        run.setText("开始推帧（盯背屏）");
        run.setAllCaps(false);
        run.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        });
        root.addView(run);

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
            StringBuilder sb = new StringBuilder("binder=" + (binder == null ? "null" : "OK"));
            try {
                Object dot = get.invoke(null, "vendor.stepos.hardware.dot_matrix.IDotMatrix/default");
                sb.append("  IDotMatrix=").append(dot == null ? "null" : "OK");
            } catch (Throwable ignored) { }
            append(sb.toString());
        } catch (Throwable t) {
            append("bind 失败 " + t);
        }
    }

    private void start() {
        if (running) return;
        if (binder == null) { bind(); if (binder == null) return; }
        running = true;
        new Thread(new Runnable() {
            @Override public void run() {
                for (int size : SIZES) {
                    for (int code : new int[]{5, 11, 12}) {
                        if (!running) break;
                        for (int on = 1; on >= 0; on--) {
                            final String label = "size=" + size + " code=" + code + (on == 1 ? " 全亮" : " 全灭");
                            runOnUiThread(new Runnable() {
                                @Override public void run() { statusView.setText(label); }
                            });
                            buzz(on == 1 ? 40 : 20);
                            String r = push(code, size, on == 1);
                            append(label + " -> " + r);
                            try { Thread.sleep(900); } catch (InterruptedException ignored) { }
                        }
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() { statusView.setText("推帧结束"); }
                });
                append("=== 结束 ===");
                running = false;
            }
        }).start();
    }

    private String push(int code, int size, boolean on) {
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            int bytes = Math.max(1, (size * size + 7) / 8);
            byte[] buf = new byte[bytes];
            for (int i = 0; i < bytes; i++) buf[i] = (byte) (on ? 0xFF : 0x00);
            d.writeInterfaceToken(DESC);
            if (code != 12) { d.writeInt(size); d.writeInt(size); }
            d.writeByteArray(buf);
            boolean ok = binder.transact(code, d, r, 0);
            int rs = r.dataSize();
            if (rs >= 4) {
                r.setDataPosition(0);
                int first = r.readInt();
                if (first == 0xFFFFFFFE) {
                    String msg = "";
                    try { msg = r.readString(); } catch (Throwable ignored) { }
                    return "EXC(" + msg + ")";
                }
            }
            return "ok=" + ok + " reply=" + rs;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            return "TRANSPORT " + c.getClass().getSimpleName();
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
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "dotmatrix_push.txt");
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

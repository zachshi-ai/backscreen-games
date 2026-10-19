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
 * 主攻 ISmomoService（display.smomoservice）：未知接口，先做签名探针，
 * 一旦发现接受 (int,int,byte[]) 的方法，立刻推 全亮/全灭/棋盘 三帧。
 */
public class MainActivity extends Activity {

    private static final String[][] SVCS = {
            {"display.smomoservice", "android.display.ISmomoService"},
            {"stepos_statusbar", "stepos.statusbar.IStepOSStatusBarService"}
    };

    private TextView logView;
    private TextView statusView;
    private String report = "";
    private volatile boolean running = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080C);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("SmomoService 探针 + 推帧");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(17);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextColor(0xFFFFD54A);
        statusView.setTextSize(13);
        statusView.setText("待开始");
        root.addView(statusView);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(9.5f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button run = new Button(this);
        run.setText("开始（盯着背屏）");
        run.setAllCaps(false);
        run.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        });
        root.addView(run);

        setContentView(root);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void start() {
        if (running) return;
        running = true;
        new Thread(new Runnable() {
            @Override public void run() {
                for (String[] s : SVCS) scan(s[0], s[1]);
                append("=== 完成 ===");
                runOnUiThread(new Runnable() {
                    @Override public void run() { statusView.setText("完成"); }
                });
                running = false;
            }
        }).start();
    }

    private void scan(String svc, String desc) {
        append("\n##### " + svc + " #####");
        IBinder b;
        try {
            Class<?> smc = Class.forName("android.os.ServiceManager");
            Method get = smc.getMethod("getService", String.class);
            Object o = get.invoke(null, svc);
            if (!(o instanceof IBinder)) { append("binder null"); return; }
            b = (IBinder) o;
            append("binder OK, desc=" + b.getInterfaceDescriptor());
        } catch (Throwable t) {
            append("bind 失败 " + t);
            return;
        }

        for (int code = 1; code <= 60; code++) {
            final String label = svc + " code=" + code;
            if (!running) break;
            runOnUiThread(new Runnable() {
                @Override public void run() { statusView.setText(label); }
            });

            String zero = call(b, desc, code, 0);
            String bmp = call(b, desc, code, 1);
            append("code=" + code + " | 无参:" + zero + " | 位图:" + bmp);

            // 若接受位图结构，立刻推三帧
            if (bmp.startsWith("CLEAN")) {
                append("  >> 该口接受位图，推帧测试");
                buzz(60);
                pushFrame(b, desc, code, 1);
                sleep(500);
                pushFrame(b, desc, code, 0);
                sleep(500);
                pushFrame(b, desc, code, 2);
                sleep(500);
            }
            sleep(90);
        }
    }

    private String call(IBinder b, String desc, int code, int mode) {
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(desc);
            if (mode == 1) {
                byte[] on = new byte[128];
                for (int i = 0; i < on.length; i++) on[i] = (byte) 0xFF;
                d.writeInt(32);
                d.writeInt(32);
                d.writeByteArray(on);
            }
            b.transact(code, d, r, 0);
            return describe(r);
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            return "TRANSPORT " + c.getClass().getSimpleName();
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    /** pattern: 1=全亮 0=全灭 2=棋盘 */
    private void pushFrame(IBinder b, String desc, int code, int pattern) {
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            byte[] buf = new byte[128];
            for (int i = 0; i < buf.length; i++) {
                if (pattern == 1) buf[i] = (byte) 0xFF;
                else if (pattern == 0) buf[i] = 0x00;
                else buf[i] = (byte) (i % 2 == 0 ? 0xAA : 0x55);
            }
            d.writeInterfaceToken(desc);
            d.writeInt(32);
            d.writeInt(32);
            d.writeByteArray(buf);
            b.transact(code, d, r, 0);
            append("     推帧 " + (pattern == 1 ? "全亮" : pattern == 0 ? "全灭" : "棋盘") + " -> " + describe(r));
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            append("     推帧异常 " + c.getClass().getSimpleName());
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    private String describe(Parcel r) {
        int size = r.dataSize();
        if (size < 4) return "CLEAN(" + size + ")";
        try {
            r.setDataPosition(0);
            int first = r.readInt();
            if (first == 0xFFFFFFFE) {
                String msg = "";
                try { msg = r.readString(); } catch (Throwable ignored) { }
                return "EXC(" + msg + ")";
            }
            return "CLEAN(" + size + ")";
        } catch (Throwable t) {
            return "READFAIL(" + size + ")";
        }
    }

    private void buzz(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Throwable ignored) { }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
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
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "smomo_scan.txt");
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

package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
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
 * 解析各 transaction 的返回内容：十六进制 + 可打印字符串。
 * 目的：从 reply 里看出接口的数据结构和方法语义（例如是否返回点阵相关字段）。
 */
public class MainActivity extends Activity {

    private static final String[][] SVCS = {
            {"display_ext", "android.hardware.display.IStepOSDisplayManager"},
            {"stepos_device_control", "android.hardware.display.IStepOSDeviceControlService"}
    };

    private TextView logView;
    private String report = "";
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
        title.setText("reply 结构解析");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(18);
        root.addView(title);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(9.5f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button run = new Button(this);
        run.setText("开始解析");
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
                running = false;
                append("\n=== 完成 ===");
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
        } catch (Throwable t) {
            append("bind 失败 " + t);
            return;
        }
        for (int code = 1; code <= 40; code++) {
            Parcel d = Parcel.obtain();
            Parcel r = Parcel.obtain();
            try {
                d.writeInterfaceToken(desc);
                d.writeInt(0);
                boolean ok = b.transact(code, d, r, 0);
                int size = r.dataSize();
                String hex = "";
                String ascii = "";
                if (ok && size > 0 && size <= 4096) {
                    r.setDataPosition(0);
                    byte[] raw = r.marshall();
                    hex = hexOf(raw, 48);
                    ascii = asciiOf(raw);
                }
                append("\ncode=" + code + " ok=" + ok + " size=" + size);
                if (!hex.isEmpty()) append("  hex: " + hex);
                if (!ascii.isEmpty()) append("  txt: " + ascii);
            } catch (Throwable t) {
                Throwable c = t.getCause() != null ? t.getCause() : t;
                append("\ncode=" + code + " 异常 " + c.getClass().getSimpleName() + ":" + c.getMessage());
            } finally {
                d.recycle();
                r.recycle();
            }
            try { Thread.sleep(120); } catch (InterruptedException ignored) { }
        }
    }

    private String hexOf(byte[] raw, int max) {
        StringBuilder s = new StringBuilder();
        int n = Math.min(raw.length, max);
        for (int i = 0; i < n; i++) {
            s.append(String.format(Locale.US, "%02x", raw[i]));
            if (i % 2 == 1) s.append(' ');
        }
        if (raw.length > max) s.append("...(").append(raw.length).append("B)");
        return s.toString();
    }

    private String asciiOf(byte[] raw) {
        StringBuilder cur = new StringBuilder();
        StringBuilder out = new StringBuilder();
        for (byte bb : raw) {
            char ch = (char) (bb & 0xFF);
            if (ch >= 32 && ch < 127) {
                cur.append(ch);
            } else {
                if (cur.length() >= 4) out.append('[').append(cur).append("] ");
                cur.setLength(0);
            }
        }
        if (cur.length() >= 4) out.append('[').append(cur).append("] ");
        return out.toString();
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
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "stepos_reply.txt");
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

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
 * 用 AIDL 异常回执做签名探针：
 * 给每个 transaction 发「宽/高/位图」结构，返回里不带 "Parcel data not fully consumed"
 * 且不带其它异常的，就是真正接受位图参数的方法。
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
        title.setText("签名探针 · 找收画面的方法");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(17);
        root.addView(title);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(10f);
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
                append("\n=== 完成 ===");
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
        } catch (Throwable t) {
            append("bind 失败 " + t);
            return;
        }
        for (int code = 1; code <= 40; code++) {
            String v1 = probe(b, desc, code, 1);
            String v2 = probe(b, desc, code, 2);
            String line = "code=" + code + " | A:" + v1 + " | B:" + v2;
            append(line);
            try { Thread.sleep(100); } catch (InterruptedException ignored) { }
        }
    }

    /** variant 1 = (32,32,byte[128]) ; 2 = (byte[128]) */
    private String probe(IBinder b, String desc, int code, int variant) {
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            byte[] on = new byte[128];
            for (int i = 0; i < on.length; i++) on[i] = (byte) 0xFF;
            d.writeInterfaceToken(desc);
            if (variant == 1) { d.writeInt(32); d.writeInt(32); }
            d.writeByteArray(on);
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

    /** 读 reply：若首字节是 0xFFFFFFFE 则是异常，解出异常文案；否则返回 CLEAN(size) */
    private String describe(Parcel r) {
        int size = r.dataSize();
        if (size < 4) return "CLEAN(" + size + ")";
        try {
            r.setDataPosition(0);
            int code = r.readInt();
            if (code == 0xFFFFFFFE) {
                String msg = "";
                try {
                    msg = r.readString();
                } catch (Throwable ignored) { }
                return "EXC(" + (msg == null ? "?" : msg) + ")";
            }
            if (code == -1) return "EXC_UNKNOWN";
            return "CLEAN(" + size + ")";
        } catch (Throwable t) {
            return "READFAIL(" + size + ")";
        }
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
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "sig_probe.txt");
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

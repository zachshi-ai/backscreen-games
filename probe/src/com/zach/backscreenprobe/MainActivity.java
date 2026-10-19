package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;

/**
 * targetSdk=27 → 绕开非 SDK 接口限制，读出 StepOS 服务接口的真实方法表。
 */
public class MainActivity extends Activity {

    private static final String[] IFACES = {
            "android.hardware.display.IStepOSDisplayManager",
            "android.hardware.display.IStepOSDeviceControlService",
            "stepos.statusbar.IStepOSStatusBarService",
            "com.stepos.theme.IStepOSThemeService"
    };

    private TextView logView;
    private String report = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080C);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("方法表导出 (targetSdk 27)");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(18);
        root.addView(title);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(10.5f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(0, dp(8), 0, dp(8));
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button run = new Button(this);
        run.setText("重新导出");
        run.setAllCaps(false);
        run.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAll(); dump(); }
        });
        root.addView(run);

        setContentView(root);
        runAll();
        dump();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void runAll() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== sdk=").append(Build.VERSION.SDK_INT)
          .append(" targetSdk=").append(getApplicationInfo().targetSdkVersion)
          .append(" uid=").append(android.os.Process.myUid()).append(" ===\n\n");

        // 1) 再看一次 IDotMatrix 是否可达
        sb.append("[0] IDotMatrix 复查\n");
        try {
            Class<?> smc = Class.forName("android.os.ServiceManager");
            Method get = smc.getMethod("getService", String.class);
            Object o = get.invoke(null, "vendor.stepos.hardware.dot_matrix.IDotMatrix/default");
            sb.append("  getService=").append(o == null ? "null" : "OK").append("\n");
        } catch (Throwable t) {
            sb.append("  异常 ").append(simple(t)).append("\n");
        }
        sb.append("\n");

        // 2) 方法表
        for (String f : IFACES) dumpIface(sb, f);

        append(sb.toString());
    }

    private void dumpIface(StringBuilder sb, String name) {
        sb.append("### ").append(name).append("\n");
        Class<?> c = null;
        try {
            c = Class.forName(name);
            sb.append("  loaded OK\n");
        } catch (Throwable t) {
            sb.append("  load 失败: ").append(simple(t)).append("\n");
        }
        if (c != null) dumpMethods(sb, c, "  ");
        for (String suffix : new String[]{"$Stub$Proxy", "$Stub"}) {
            try {
                Class<?> k = Class.forName(name + suffix);
                sb.append("  [").append(suffix).append("]\n");
                dumpMethods(sb, k, "    ");
            } catch (Throwable t) {
                sb.append("  [").append(suffix).append("] ").append(simple(t)).append("\n");
            }
        }
        sb.append("\n");
    }

    private void dumpMethods(StringBuilder sb, Class<?> c, String indent) {
        try {
            Method[] ms = c.getDeclaredMethods();
            sb.append(indent).append("methods(").append(ms.length).append(")\n");
            for (Method m : ms) {
                StringBuilder s = new StringBuilder();
                s.append(indent).append("  ").append(m.getReturnType().getSimpleName())
                 .append(" ").append(m.getName()).append("(");
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    if (i > 0) s.append(", ");
                    s.append(ps[i].getSimpleName());
                }
                s.append(")\n");
                sb.append(s);
            }
        } catch (Throwable t) {
            sb.append(indent).append("methods 失败: ").append(simple(t)).append("\n");
        }
    }

    private String simple(Throwable t) {
        Throwable c = t.getCause() != null ? t.getCause() : t;
        return c.getClass().getSimpleName() + ":" + c.getMessage();
    }

    private void append(String s) {
        report = report + s;
        logView.setText(report);
    }

    private void dump() {
        try {
            byte[] bytes = report.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "stepos_api27.txt");
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                    if (os != null) { os.write(bytes); os.flush(); os.close(); }
                }
            } else {
                java.io.File f = new java.io.File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "stepos_api27.txt");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                fo.write(bytes); fo.flush(); fo.close();
            }
        } catch (Throwable ignored) { }
    }
}

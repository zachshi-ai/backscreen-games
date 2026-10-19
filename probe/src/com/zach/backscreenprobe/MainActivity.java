package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;

/**
 * 把可达的 StepOS 框架服务接口方法全量导出，找点阵相关调用。
 */
public class MainActivity extends Activity {

    // 上一轮实测可解析到 binder 的描述符
    private static final String[] IFACES = {
            "android.hardware.display.IStepOSDeviceControlService",
            "android.hardware.display.IStepOSDisplayManager",
            "android.display.ISmomoService",
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
        title.setText("StepOS 服务接口导出");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(19);
        root.addView(title);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(11f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(0, dp(8), 0, dp(8));
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button scan = new Button(this);
        scan.setText("重新导出");
        scan.setAllCaps(false);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { scanAll(); dump(); }
        });
        root.addView(scan);

        setContentView(root);
        scanAll();
        dump();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void scanAll() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== 接口导出 =====\n");
        sb.append("sdk=").append(Build.VERSION.SDK_INT)
          .append(" uid=").append(android.os.Process.myUid()).append("\n\n");
        for (String f : IFACES) dumpClass(sb, f);
        append(sb.toString());
    }

    private void dumpClass(StringBuilder sb, String name) {
        sb.append("### ").append(name).append("\n");
        Class<?> c = null;
        try {
            c = Class.forName(name);
            sb.append("  loaded: ").append(c.getName()).append("\n");
        } catch (Throwable t) {
            sb.append("  Class.forName 失败: ").append(simple(t)).append("\n");
        }
        if (c != null) {
            dumpMethods(sb, c, "  ");
            try {
                for (Class<?> inner : c.getDeclaredClasses()) {
                    sb.append("  [inner] ").append(inner.getSimpleName()).append("\n");
                    dumpMethods(sb, inner, "    ");
                }
            } catch (Throwable t) {
                sb.append("  inner 读取失败: ").append(simple(t)).append("\n");
            }
        }
        // 常见 AIDL 生成类命名
        for (String suffix : new String[]{"$Stub", "$Stub$Proxy"}) {
            try {
                Class<?> k = Class.forName(name + suffix);
                sb.append("  [aidl] ").append(k.getName()).append(" OK\n");
                dumpMethods(sb, k, "    ");
            } catch (Throwable t) {
                sb.append("  [aidl] ").append(name).append(suffix).append(" -> ").append(simple(t)).append("\n");
            }
        }
        sb.append("\n");
    }

    private void dumpMethods(StringBuilder sb, Class<?> c, String indent) {
        try {
            Method[] ms = c.getDeclaredMethods();
            sb.append(indent).append("methods(").append(ms.length).append("):\n");
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
        return t.getClass().getSimpleName() + ":" + t.getMessage();
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
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "stepos_iface.txt");
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

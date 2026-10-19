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
 * 点阵服务可达性实验：
 * 1) 批量 getService 各候选服务名，记录 null / 非 null / 异常
 * 2) 验证是否为懒加载 HAL —— 让系统先用一次背屏后再查
 */
public class MainActivity extends Activity {

    private static final String[] SVCS = {
            "vendor.stepos.hardware.dot_matrix.IDotMatrix/default",
            "vendor.stepos.hardware.dot_matrix.IDotMatrix",
            "IDotMatrix/default",
            "IDotMatrix",
            "dot_matrix",
            "dotmatrix",
            "stepos_dot_matrix",
            "vendor.stepos.hardware.display.ISteposDisplay/default",
            "vendor.stepos.hardware.display.cwb.ISteposCwb/default",
            "ISteposDisplay/default",
            "stepos_device_control",
            "stepos_gesture",
            "stepos_statusbar",
            "stepos_theme",
            "stepscene",
            "display",
            "display_ext",
            "display.smomoservice",
            "lights",
            "alphame_server"
    };

    private TextView logView;
    private String report = "";
    private Method getService;
    private Method checkService;
    private int round = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080C);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("点阵可达性 · 懒加载验证");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(19);
        root.addView(title);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(11.5f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(0, dp(8), 0, dp(8));
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button scan = new Button(this);
        scan.setText("重试获取（先去系统设置里调一下背屏）");
        scan.setAllCaps(false);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { scanAll("手动重试"); dump(); }
        });
        root.addView(scan);

        setContentView(root);
        scanAll("首次启动");
        dump();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private boolean init() {
        try {
            Class<?> smc = Class.forName("android.os.ServiceManager");
            getService = smc.getMethod("getService", String.class);
            try { checkService = smc.getMethod("checkService", String.class); } catch (Throwable ignored) { }
            return true;
        } catch (Throwable t) {
            append("[反射失败] " + t + "\n");
            return false;
        }
    }

    private void scanAll(String tag) {
        round++;
        StringBuilder sb = new StringBuilder();
        sb.append("\n===== 第 ").append(round).append(" 轮：").append(tag).append(" =====\n");
        sb.append("sdk=").append(Build.VERSION.SDK_INT)
          .append("  uid=").append(android.os.Process.myUid()).append("\n");
        if (getService == null && !init()) { return; }

        for (String name : SVCS) {
            sb.append(name).append("  ->  ");
            try {
                Object o = getService.invoke(null, name);
                if (o == null) {
                    sb.append("null");
                    if (checkService != null) {
                        try {
                            Object c = checkService.invoke(null, name);
                            sb.append("  checkService=").append(c == null ? "null" : c.getClass().getSimpleName());
                        } catch (Throwable t) {
                            sb.append("  checkService异常=").append(simple(t));
                        }
                    }
                    sb.append("\n");
                } else {
                    sb.append("OK  class=").append(o.getClass().getName());
                    if (o instanceof IBinder) {
                        IBinder bi = (IBinder) o;
                        sb.append(" alive=").append(bi.isBinderAlive());
                        try {
                            sb.append(" desc=").append(bi.getInterfaceDescriptor());
                        } catch (Throwable t) {
                            sb.append(" desc异常=").append(simple(t));
                        }
                    }
                    sb.append("\n");
                }
            } catch (Throwable t) {
                sb.append("异常 ").append(simple(t)).append("\n");
            }
        }
        append(sb.toString());
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
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "dotmatrix_access.txt");
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

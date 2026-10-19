package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * 1) 抓 smomo 各方法的 SecurityException 文案（通常写明所需权限）
 * 2) 用更宽的词表重扫组件，并整包导出 settings / stepos 系应用的对外组件
 */
public class MainActivity extends Activity {

    private static final String[] KW = {
            "led", "light", "pattern", "back", "pixel", "panel", "ambient", "aod", "cover",
            "second", "matrix", "dot", "glyph", "rear", "smomo", "cwb", "extscreen", "subscreen"
    };

    private static final String[] FULL_DUMP_PKGS = {
            "com.android.settings", "com.android.systemui", "com.val.hardware", "com.stepos.systemuiex"
    };

    private TextView logView;
    private TextView statusView;
    private String report = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF07070C);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("背屏通道 · 权限与组件");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(19);
        title.setLetterSpacing(0.02f);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText("待开始");
        statusView.setTextColor(0xFFFFD54A);
        statusView.setTextSize(13);
        statusView.setPadding(0, dp(8), 0, dp(10));
        root.addView(statusView);

        logView = new TextView(this);
        logView.setTextColor(0xFFDADAE6);
        logView.setTextSize(9.5f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF101018);
        bg.setCornerRadius(dp(12));
        logView.setBackground(bg);
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button run = new Button(this);
        run.setText("开始扫描");
        run.setAllCaps(false);
        run.setTextColor(0xFF07070C);
        GradientDrawable btn = new GradientDrawable();
        btn.setColor(0xFF4FF0C0);
        btn.setCornerRadius(dp(12));
        run.setBackground(btn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(12);
        run.setLayoutParams(lp);
        run.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        });
        root.addView(run);

        setContentView(root);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void start() {
        new Thread(new Runnable() {
            @Override public void run() {
                smomoPermissions();
                componentScan();
                setStatus("完成");
            }
        }).start();
    }

    /* ---------- 1. smomo 权限文案 ---------- */
    private void smomoPermissions() {
        append("\n### smomo 权限校验文案 ###");
        IBinder b;
        try {
            Class<?> smc = Class.forName("android.os.ServiceManager");
            Method get = smc.getMethod("getService", String.class);
            Object o = get.invoke(null, "display.smomoservice");
            if (!(o instanceof IBinder)) { append("binder null"); return; }
            b = (IBinder) o;
        } catch (Throwable t) {
            append("bind 失败 " + t);
            return;
        }
        for (int code = 1; code <= 25; code++) {
            Parcel d = Parcel.obtain();
            Parcel r = Parcel.obtain();
            try {
                d.writeInterfaceToken("android.display.ISmomoService");
                d.writeInt(0);
                b.transact(code, d, r, 0);
                append("code=" + code + " -> CLEAN size=" + r.dataSize());
            } catch (Throwable t) {
                Throwable c = t.getCause() != null ? t.getCause() : t;
                append("code=" + code + " -> " + c.getClass().getSimpleName() + ": " + c.getMessage());
            } finally {
                d.recycle();
                r.recycle();
            }
        }
    }

    /* ---------- 2. 宽词表 + 整包导出 ---------- */
    private void componentScan() {
        append("\n### 组件宽扫 ###");
        try {
            PackageManager pm = getPackageManager();
            List<PackageInfo> pis = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PROVIDERS);
            for (PackageInfo pi : pis) {
                boolean full = false;
                for (String p : FULL_DUMP_PKGS) if (p.equals(pi.packageName)) full = true;
                StringBuilder block = new StringBuilder();
                int hitCount = 0;

                if (pi.activities != null) {
                    for (ActivityInfo a : pi.activities) {
                        if (full || hit(a.name)) {
                            block.append("  A ").append(a.name)
                                 .append(" exported=").append(a.exported)
                                 .append(a.permission == null ? "" : (" perm=" + a.permission)).append("\n");
                            hitCount++;
                        }
                    }
                }
                if (pi.services != null) {
                    for (ServiceInfo s : pi.services) {
                        if (full || hit(s.name)) {
                            block.append("  S ").append(s.name)
                                 .append(" exported=").append(s.exported)
                                 .append(s.permission == null ? "" : (" perm=" + s.permission)).append("\n");
                            hitCount++;
                        }
                    }
                }
                if (pi.providers != null) {
                    for (ProviderInfo p : pi.providers) {
                        if (full || hit(p.name)) {
                            block.append("  P ").append(p.name)
                                 .append(" auth=").append(p.authority)
                                 .append(" exported=").append(p.exported).append("\n");
                            hitCount++;
                        }
                    }
                }
                if (hitCount > 0) {
                    append("\n[pkg] " + pi.packageName + (full ? "  <整包>" : ""));
                    append(block.toString());
                }
            }
        } catch (Throwable t) {
            append("扫描异常 " + t);
        }
    }

    private boolean hit(String s) {
        String l = s.toLowerCase(Locale.US);
        for (String k : KW) if (l.contains(k)) return true;
        return false;
    }

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() {
            @Override public void run() { statusView.setText(s); }
        });
    }

    private void append(String s) {
        report = report + s + "\n";
        String snapshot = report;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                logView.setText(snapshot);
            }
        });
        dump(snapshot);
    }

    private void dump(String content) {
        try {
            byte[] bytes = content.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "perm_n_comp.txt");
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

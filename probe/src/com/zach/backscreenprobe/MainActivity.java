package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.GradientDrawable;
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

import java.util.List;
import java.util.Locale;

/**
 * 逐包隔离的组件扫描：重点导出 com.stepos.* / com.val.* / settings / systemui 的对外组件，
 * 其余包按宽词表筛选。每个包独立 try/catch，避免一个异常中断全表。
 */
public class MainActivity extends Activity {

    private static final String[] KW = {
            "led", "light", "pattern", "back", "pixel", "panel", "ambient", "aod", "cover",
            "second", "matrix", "dot", "glyph", "rear", "smomo", "cwb", "extscreen", "subscreen", "display"
    };

    private TextView logView;
    private TextView statusView;
    private String report = "";
    private int pkgIndex = 0, pkgTotal = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF07070C);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("背屏组件 · 全量扫描");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(19);
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
                try {
                    scan();
                } catch (Throwable t) {
                    append("[FATAL] " + t);
                }
                setStatus("完成");
            }
        }).start();
    }

    private void scan() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> pis = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES
                | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                | PackageManager.GET_PROVIDERS);
        pkgTotal = pis.size();
        append("包总数: " + pkgTotal);

        for (PackageInfo pi : pis) {
            pkgIndex++;
            try {
                handlePackage(pi);
            } catch (Throwable t) {
                append("[skip] " + pi.packageName + " -> " + t.getClass().getSimpleName());
            }
            if (pkgIndex % 25 == 0) {
                final String s = "进度 " + pkgIndex + "/" + pkgTotal;
                runOnUiThread(new Runnable() {
                    @Override public void run() { statusView.setText(s); }
                });
            }
        }
        append("=== 扫描结束 " + pkgIndex + "/" + pkgTotal + " ===");
    }

    private void handlePackage(PackageInfo pi) {
        String pkg = pi.packageName;
        boolean full = pkg.startsWith("com.stepos.") || pkg.startsWith("com.val.") || pkg.startsWith("com.stepx.")
                || pkg.equals("com.android.settings") || pkg.equals("com.android.systemui");

        StringBuilder block = new StringBuilder();
        int hits = 0;

        if (pi.activities != null) {
            for (ActivityInfo a : pi.activities) {
                if (a == null) continue;
                if (full || hit(a.name)) {
                    block.append("  A ").append(a.name)
                         .append(" exported=").append(a.exported)
                         .append(a.permission == null ? "" : (" perm=" + a.permission)).append("\n");
                    hits++;
                }
            }
        }
        if (pi.services != null) {
            for (ServiceInfo s : pi.services) {
                if (s == null) continue;
                if (full || hit(s.name)) {
                    block.append("  S ").append(s.name)
                         .append(" exported=").append(s.exported)
                         .append(s.permission == null ? "" : (" perm=" + s.permission)).append("\n");
                    hits++;
                }
            }
        }
        if (pi.receivers != null) {
            for (ActivityInfo r : pi.receivers) {
                if (r == null) continue;
                if (full || hit(r.name)) {
                    block.append("  R ").append(r.name)
                         .append(" exported=").append(r.exported).append("\n");
                    hits++;
                }
            }
        }
        if (pi.providers != null) {
            for (ProviderInfo p : pi.providers) {
                if (p == null) continue;
                if (full || hit(p.name)) {
                    block.append("  P ").append(p.name)
                         .append(" auth=").append(p.authority)
                         .append(" exported=").append(p.exported).append("\n");
                    hits++;
                }
            }
        }
        if (hits > 0) {
            append("\n[pkg] " + pkg + (full ? "  <整包>" : ""));
            append(block.toString());
        }
    }

    private boolean hit(String s) {
        if (s == null) return false;
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
        final String snap = report;
        runOnUiThread(new Runnable() {
            @Override public void run() { logView.setText(snap); }
        });
        dump(snap);
    }

    private void dump(String content) {
        try {
            byte[] bytes = content.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "stepos_comp.txt");
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

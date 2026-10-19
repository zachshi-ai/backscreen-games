package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
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

/** 打开 com.val.hardware 的背屏测试界面，并导出它的全部组件。 */
public class MainActivity extends Activity {

    private static final String TEST_PKG = "com.val.hardware";
    private static final String[][] TARGETS = {
            {TEST_PKG, "com.val.hardware.SecondScreen.SecondScreenActivity"},
            {TEST_PKG, "com.val.hardware.SecondScreenColor.SecondScreenColorActivity"},
            {"com.android.settings", "com.stepos.settings.StepOSSettingsActivity$StepOSBackScreenActivity"}
    };

    private TextView logView;
    private String report = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080C);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("背屏测试入口");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(18);
        root.addView(title);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(10f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addButton(root, "打开 SecondScreen 测试");
        addButton(root, "打开 SecondScreenColor 测试");
        addButton(root, "打开系统背屏设置");

        Button dumpBtn = new Button(this);
        dumpBtn.setText("导出 com.val.hardware 全部组件");
        dumpBtn.setAllCaps(false);
        dumpBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dumpPkg(); dump(); }
        });
        root.addView(dumpBtn);

        setContentView(root);
        dumpPkg();
        dump();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void addButton(LinearLayout root, final String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int idx = label.contains("Color") ? 1 : (label.contains("系统") ? 2 : 0);
                launch(TARGETS[idx][0], TARGETS[idx][1]);
            }
        });
        root.addView(b);
    }

    private void launch(String pkg, String cls) {
        try {
            Intent i = new Intent();
            i.setClassName(pkg, cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            append("启动成功: " + cls);
        } catch (ActivityNotFoundException e) {
            append("未找到: " + cls);
        } catch (Throwable t) {
            append("启动失败: " + cls + " -> " + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
    }

    private void dumpPkg() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== com.val.hardware 组件 ===\n");
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(TEST_PKG, PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PROVIDERS);
            if (pi.activities != null) {
                sb.append("[Activities]\n");
                for (ActivityInfo a : pi.activities) {
                    sb.append("  A ").append(a.name)
                      .append(" exported=").append(a.exported)
                      .append(" perm=").append(a.permission).append("\n");
                }
            }
            if (pi.services != null) {
                sb.append("[Services]\n");
                for (ServiceInfo s : pi.services) {
                    sb.append("  S ").append(s.name)
                      .append(" exported=").append(s.exported)
                      .append(" perm=").append(s.permission).append("\n");
                }
            }
            if (pi.receivers != null) {
                sb.append("[Receivers]\n");
                for (ActivityInfo r : pi.receivers) {
                    sb.append("  R ").append(r.name)
                      .append(" exported=").append(r.exported).append("\n");
                }
            }
            if (pi.providers != null) {
                sb.append("[Providers]\n");
                for (ProviderInfo p : pi.providers) {
                    sb.append("  P ").append(p.name)
                      .append(" authority=").append(p.authority)
                      .append(" exported=").append(p.exported).append("\n");
                }
            }
        } catch (Throwable t) {
            sb.append("读取失败: ").append(t.getClass().getSimpleName()).append(":")
              .append(t.getMessage()).append("\n");
        }
        append(sb.toString());
    }

    private void append(String s) {
        report = report + s + "\n";
        logView.setText(report);
    }

    private void dump() {
        try {
            byte[] bytes = report.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "val_hardware.txt");
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

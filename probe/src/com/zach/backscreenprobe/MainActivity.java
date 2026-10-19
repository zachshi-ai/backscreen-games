package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
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

import java.util.List;
import java.util.Locale;

/** 全机扫描：找出组件名/包名里与背屏相关的项，并标出是否 exported。 */
public class MainActivity extends Activity {

    private static final String[] KW = {
            "second", "cover", "matrix", "dot", "glyph", "rear", "smomo", "cwb", "backscreen", "back_screen"
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
        title.setText("背屏入口扫描");
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

        Button run = new Button(this);
        run.setText("重新扫描");
        run.setAllCaps(false);
        run.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { scan(); dump(); }
        });
        root.addView(run);

        setContentView(root);
        scan();
        dump();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void scan() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 背屏相关组件扫描 ===\n");
        try {
            PackageManager pm = getPackageManager();
            List<PackageInfo> pis = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PROVIDERS);
            int found = 0;
            for (PackageInfo pi : pis) {
                String pkg = pi.packageName.toLowerCase(Locale.US);
                boolean pkgHit = hit(pkg);
                StringBuilder block = new StringBuilder();
                int comp = 0;

                if (pi.activities != null) {
                    for (ActivityInfo a : pi.activities) {
                        if (hit(a.name.toLowerCase(Locale.US))) {
                            block.append("  A ").append(a.name)
                                 .append(" exported=").append(a.exported)
                                 .append(" perm=").append(a.permission).append("\n");
                            comp++;
                        }
                    }
                }
                if (pi.services != null) {
                    for (ServiceInfo s : pi.services) {
                        if (hit(s.name.toLowerCase(Locale.US))) {
                            block.append("  S ").append(s.name)
                                 .append(" exported=").append(s.exported)
                                 .append(" perm=").append(s.permission).append("\n");
                            comp++;
                        }
                    }
                }
                if (pi.receivers != null) {
                    for (ActivityInfo r : pi.receivers) {
                        if (hit(r.name.toLowerCase(Locale.US))) {
                            block.append("  R ").append(r.name)
                                 .append(" exported=").append(r.exported)
                                 .append(" perm=").append(r.permission).append("\n");
                            comp++;
                        }
                    }
                }
                if (pi.providers != null) {
                    for (ProviderInfo p : pi.providers) {
                        if (hit(p.name.toLowerCase(Locale.US)) || (p.authority != null && hit(p.authority.toLowerCase(Locale.US)))) {
                            block.append("  P ").append(p.name)
                                 .append(" authority=").append(p.authority)
                                 .append(" exported=").append(p.exported).append("\n");
                            comp++;
                        }
                    }
                }
                if (comp > 0 || pkgHit) {
                    found++;
                    sb.append("\n[pkg] ").append(pi.packageName)
                      .append(pkgHit ? "  <包名命中>" : "").append("\n");
                    sb.append(block);
                }
            }
            sb.append("\n命中包数: ").append(found).append("\n");
        } catch (Throwable t) {
            sb.append("扫描异常: ").append(t).append("\n");
        }
        report = report + sb;
        logView.setText(report);
    }

    private boolean hit(String s) {
        for (String k : KW) if (s.contains(k)) return true;
        return false;
    }

    private void dump() {
        try {
            byte[] bytes = report.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "back_entry.txt");
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

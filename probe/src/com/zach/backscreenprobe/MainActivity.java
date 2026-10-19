package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public class MainActivity extends Activity implements SensorEventListener, SnakeView.TiltSource {

    private DisplayManager dm;
    private SensorManager sm;
    private TextView logView;
    private TiltView tiltView;
    private String lastReport = "";
    private final Handler ui = new Handler(Looper.getMainLooper());

    private float ax, ay, az, gx, gy, gz;
    private boolean hasAcc, hasGyro;

    private static final String[] KEYWORDS = {
            "back", "rear", "cover", "led", "dot", "matrix", "glyph", "sub", "second", "flip",
            "screen", "display", "panel", "matrix", "pixel", "light", "notify", "smart", "step",
            "背", "副", "点阵"
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread th, Throwable e) {
                try {
                    java.io.File f = new java.io.File(getExternalFilesDir(null), "crash.txt");
                    java.io.PrintWriter w = new java.io.PrintWriter(f);
                    w.println(new java.util.Date().toString());
                    e.printStackTrace(w);
                    w.close();
                } catch (Throwable ignored) { }
                if (prev != null) prev.uncaughtException(th, e);
            }
        });

        dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080C);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("点阵背屏 · 通道探测");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(19);
        root.addView(title);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(12f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(0, dp(8), 0, dp(8));
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tiltView = new TiltView(this);
        root.addView(tiltView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)));

        Button deep = new Button(this);
        deep.setText("深度探测（包/组件/系统键）");
        deep.setAllCaps(false);
        deep.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override public void run() { deepProbe(); }
                }).start();
            }
        });
        root.addView(deep);

        Button copy = new Button(this);
        copy.setText("重新写入报告文件");
        copy.setAllCaps(false);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dumpToDownloads(lastReport); toast("已写入 下载/backscreen_probe.txt"); }
        });
        root.addView(copy);

        TextView hint = new TextView(this);
        hint.setText("报告自动写到「下载/backscreen_probe.txt」，可重复写入（会带序号）。");
        hint.setTextColor(0xFF6D6D7A);
        hint.setTextSize(11f);
        root.addView(hint);

        setContentView(root);
        probe();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(final String s) {
        ui.post(new Runnable() { @Override public void run() { Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show(); } });
    }

    /* ================= 基础探测：显示器 + 传感器 ================= */
    private void probe() {
        StringBuilder sb = new StringBuilder();
        Display[] ds = dm.getDisplays();
        sb.append("显示器数量: ").append(ds.length).append("\n\n");
        for (Display d : ds) {
            DisplayMetrics m = new DisplayMetrics();
            try { d.getRealMetrics(m); } catch (Throwable t) { m = null; }
            sb.append("id=").append(d.getDisplayId()).append("  name=").append(d.getName()).append("\n");
            if (m != null) {
                sb.append("   像素 ").append(m.widthPixels).append(" × ").append(m.heightPixels)
                        .append("  dpi=").append(m.densityDpi).append("\n");
            }
            sb.append("   flags=0x").append(Integer.toHexString(d.getFlags()))
                    .append(" (").append(flagText(d.getFlags())).append(")\n");
            sb.append("   state=").append(d.getState())
                    .append("  rotation=").append(d.getRotation())
                    .append("  refresh=").append(String.format(Locale.US, "%.1f", d.getRefreshRate())).append("Hz\n");
            sb.append("   presentation=").append((d.getFlags() & Display.FLAG_PRESENTATION) != 0 ? "YES" : "NO").append("\n\n");
        }

        sb.append("传感器:\n");
        hasAcc = false; hasGyro = false;
        List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : all) {
            if (s.getType() == Sensor.TYPE_ACCELEROMETER) hasAcc = true;
            if (s.getType() == Sensor.TYPE_GYROSCOPE) hasGyro = true;
            if (s.getType() == Sensor.TYPE_ACCELEROMETER || s.getType() == Sensor.TYPE_GYROSCOPE
                    || s.getType() == Sensor.TYPE_GRAVITY || s.getType() == Sensor.TYPE_MAGNETIC_FIELD
                    || s.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                sb.append("  · ").append(s.getName()).append("  type=").append(s.getType())
                        .append("  vendor=").append(s.getVendor()).append("\n");
            }
        }
        sb.append("  加速度计=").append(hasAcc ? "YES" : "NO")
                .append("  陀螺仪=").append(hasGyro ? "YES" : "NO").append("\n\n");
        sb.append("结论: 背屏不是 Android 显示器，Presentation 通道不可用。\n");
        sb.append("下一步看“深度探测”里有没有可驱动的系统组件。\n");

        lastReport = sb.toString();
        logView.setText(lastReport);
        dumpToDownloads(lastReport);
    }

    private String flagText(int f) {
        StringBuilder s = new StringBuilder();
        if ((f & Display.FLAG_SUPPORTS_PROTECTED_BUFFERS) != 0) s.append("PROTECTED_BUFFERS ");
        if ((f & Display.FLAG_SECURE) != 0) s.append("SECURE ");
        if ((f & Display.FLAG_PRIVATE) != 0) s.append("PRIVATE ");
        if ((f & Display.FLAG_PRESENTATION) != 0) s.append("PRESENTATION ");
        if ((f & Display.FLAG_ROUND) != 0) s.append("ROUND ");
        if (f == 0) s.append("none");
        return s.toString().trim();
    }

    /* ================= 深度探测：包 / 组件 / 系统键 ================= */
    private void deepProbe() {
        StringBuilder sb = new StringBuilder();
        try {
            PackageManager pm = getPackageManager();
            List<PackageInfo> pis = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PROVIDERS);

            sb.append("\n\n===== 深度探测 =====\n");
            sb.append("已安装包总数: ").append(pis.size()).append("\n");

            TreeSet<String> allNames = new TreeSet<String>();
            for (PackageInfo pi : pis) {
                allNames.add(pi.packageName);
                String pkg = pi.packageName.toLowerCase(Locale.US);
                String label = "";
                try { label = String.valueOf(pi.applicationInfo.loadLabel(pm)); } catch (Throwable ignored) { }
                String hay = pkg + " " + label.toLowerCase(Locale.US);
                boolean hit = false;
                for (String k : KEYWORDS) { if (hay.contains(k.toLowerCase(Locale.US))) { hit = true; break; } }
                if (!hit) continue;

                int fl = pi.applicationInfo.flags;
                boolean sys = (fl & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean upd = (fl & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                sb.append("\n[pkg] ").append(pi.packageName).append("  label=").append(label)
                        .append("  system=").append(sys).append(" updatedSystem=").append(upd).append("\n");

                if (pi.activities != null) {
                    for (ActivityInfo a : pi.activities) {
                        sb.append("  A ").append(a.name).append(" exported=").append(a.exported)
                                .append(" perm=").append(a.permission).append("\n");
                    }
                }
                if (pi.services != null) {
                    for (ServiceInfo s : pi.services) {
                        sb.append("  S ").append(s.name).append(" exported=").append(s.exported)
                                .append(" perm=").append(s.permission).append("\n");
                    }
                }
                if (pi.receivers != null) {
                    for (ActivityInfo r : pi.receivers) {
                        sb.append("  R ").append(r.name).append(" exported=").append(r.exported)
                                .append(" perm=").append(r.permission).append("\n");
                    }
                }
                if (pi.providers != null) {
                    for (ProviderInfo p : pi.providers) {
                        sb.append("  P ").append(p.name).append(" authority=").append(p.authority)
                                .append(" exported=").append(p.exported).append("\n");
                    }
                }
            }

            sb.append("\n===== Settings 关键词项 =====\n");
            dumpSettings(sb, "SECURE", getAllSettings("android.provider.Settings$Secure"));
            dumpSettings(sb, "GLOBAL", getAllSettings("android.provider.Settings$Global"));

            sb.append("\n===== ServiceManager =====\n");
            try {
                Class<?> smc = Class.forName("android.os.ServiceManager");
                Method m = smc.getMethod("listServices");
                String[] names = (String[]) m.invoke(null);
                if (names != null) {
                    for (String n : names) sb.append("  svc: ").append(n).append("\n");
                }
            } catch (Throwable e) {
                sb.append("  不可用: ").append(e.getClass().getSimpleName()).append(" ").append(e.getMessage()).append("\n");
            }

            sb.append("\n===== 全部包名 =====\n");
            for (String n : allNames) sb.append(n).append("\n");

        } catch (Throwable e) {
            sb.append("\n深度探测异常: ").append(e).append("\n");
        }

        final String out = sb.toString();
        ui.post(new Runnable() {
            @Override public void run() {
                lastReport = lastReport + out;
                logView.setText(lastReport);
                dumpToDownloads(lastReport);
                toast("深度探测完成，已写入文件");
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> getAllSettings(String cls) {
        try {
            Class<?> c = Class.forName(cls);
            Method m = c.getMethod("getAll", android.content.ContentResolver.class);
            return (Map<String, ?>) m.invoke(null, getContentResolver());
        } catch (Throwable e) {
            return null;
        }
    }

    private void dumpSettings(StringBuilder sb, String kind, Map<String, ?> all) {
        if (all == null) { sb.append(kind).append(": 不可读\n"); return; }
        int n = 0;
        for (Map.Entry<String, ?> e : all.entrySet()) {
            String k = e.getKey().toLowerCase(Locale.US);
            boolean hit = false;
            for (String kw : KEYWORDS) { if (k.contains(kw)) { hit = true; break; } }
            if (!hit) continue;
            sb.append("  ").append(kind).append(" ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
            if (++n > 300) break;
        }
        if (n == 0) sb.append(kind).append(": 无匹配项\n");
    }

    /* ================= 文件落地 ================= */
    private void dumpToDownloads(String content) {
        if (content == null || content.length() == 0) return;
        try {
            byte[] bytes = content.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "backscreen_probe.txt");
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                    if (os != null) { os.write(bytes); os.flush(); os.close(); }
                }
            } else {
                java.io.File f = new java.io.File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "backscreen_probe.txt");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                fo.write(bytes); fo.flush(); fo.close();
            }
        } catch (Throwable ignored) { }
    }

    /* ================= 传感器 ================= */
    @Override
    protected void onResume() {
        super.onResume();
        List<Sensor> acc = sm.getSensorList(Sensor.TYPE_ACCELEROMETER);
        List<Sensor> gyro = sm.getSensorList(Sensor.TYPE_GYROSCOPE);
        hasAcc = !acc.isEmpty();
        hasGyro = !gyro.isEmpty();
        if (hasAcc) sm.registerListener(this, acc.get(0), SensorManager.SENSOR_DELAY_GAME);
        if (hasGyro) sm.registerListener(this, gyro.get(0), SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            ax = e.values[0]; ay = e.values[1]; az = e.values[2];
        } else if (e.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gx = e.values[0]; gy = e.values[1]; gz = e.values[2];
        }
        if (tiltView != null) tiltView.update(ax, ay, az, gx, gy, gz);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public float getAx() { return ax; }
    @Override public float getAy() { return ay; }
    @Override public float getAz() { return az; }
}

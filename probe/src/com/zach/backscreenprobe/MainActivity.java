package com.zach.backscreenprobe;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener, SnakeView.TiltSource {

    private DisplayManager dm;
    private SensorManager sm;
    private TextView logView;
    private TiltView tiltView;
    private SnakeView mainSnake;
    private Presentation backPres;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private float ax, ay, az, gx, gy, gz;
    private boolean hasAcc, hasGyro;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080C);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("背屏探测 · 体感");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(20);
        root.addView(title);

        logView = new TextView(this);
        logView.setTextColor(0xFFE8E8F0);
        logView.setTextSize(12.5f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(0, dp(8), 0, dp(8));
        ScrollView sc = new ScrollView(this);
        sc.addView(logView);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tiltView = new TiltView(this);
        root.addView(tiltView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));

        Button rescan = new Button(this);
        rescan.setText("重新扫描显示器 / 重开背屏");
        rescan.setAllCaps(false);
        rescan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                probe();
                showOnBackScreen();
            }
        });
        root.addView(rescan);

        TextView hint = new TextView(this);
        hint.setText("若背屏被系统识别为独立显示器，这里会自动把体感贪吃蛇投到背屏；否则退化为全屏演示。");
        hint.setTextColor(0xFF6D6D7A);
        hint.setTextSize(11.5f);
        hint.setPadding(0, dp(6), 0, dp(6));
        root.addView(hint);

        setContentView(root);
        probe();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /* ---------- 显示器探测 ---------- */
    private void probe() {
        StringBuilder sb = new StringBuilder();
        Display[] ds = dm.getDisplays();
        sb.append("显示器数量: ").append(ds.length).append("\n\n");
        for (Display d : ds) {
            DisplayMetrics m = new DisplayMetrics();
            try { d.getRealMetrics(m); } catch (Throwable t) { m = null; }
            sb.append("id=").append(d.getDisplayId())
              .append("  name=").append(d.getName()).append("\n");
            if (m != null) {
                sb.append("   像素 ").append(m.widthPixels).append(" × ").append(m.heightPixels)
                  .append("  dpi=").append(m.densityDpi)
                  .append("  比例 ").append(String.format(Locale.US, "%.2f",
                        (float) Math.max(m.widthPixels, m.heightPixels)
                                / Math.max(1, Math.min(m.widthPixels, m.heightPixels)))).append("\n");
            }
            sb.append("   flags=0x").append(Integer.toHexString(d.getFlags()))
              .append(d.getFlags() == 0 ? "" : " (").append(flagText(d.getFlags())).append(d.getFlags() == 0 ? "" : ")")
              .append("\n");
            sb.append("   状态 ").append(stateText(d.getState()))
              .append("  旋转 ").append(d.getRotation()).append("°")
              .append("  刷新 ").append(String.format(Locale.US, "%.1f", d.getRefreshRate())).append("Hz")
              .append("\n");
            sb.append("   可作展示屏(presentation)=")
              .append((d.getFlags() & Display.FLAG_PRESENTATION) != 0 ? "是" : "否").append("\n\n");
        }

        sb.append("传感器:\n");
        List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : all) {
            String n = s.getName();
            if (s.getType() == Sensor.TYPE_ACCELEROMETER
                    || s.getType() == Sensor.TYPE_GYROSCOPE
                    || s.getType() == Sensor.TYPE_GRAVITY
                    || s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                sb.append("  · ").append(n).append("  type=").append(s.getType())
                  .append("  厂商=").append(s.getVendor()).append("\n");
            }
        }
        sb.append("  （加速度计 ").append(hasAcc ? "有" : "无")
          .append(" / 陀螺仪 ").append(hasGyro ? "有" : "无").append("）\n\n");

        sb.append("结论: ");
        if (ds.length > 1) {
            sb.append("检测到多块显示器，尝试投送背屏。");
        } else {
            sb.append("系统只暴露 1 块显示器——背屏内容由系统侧接管，未作为 Android 独立显示器开放给应用。");
        }
        logView.setText(sb.toString());
    }

    private String flagText(int f) {
        StringBuilder s = new StringBuilder();
        if ((f & Display.FLAG_SUPPORTS_PROTECTED_BUFFERS) != 0) s.append("SECURE ");
        if ((f & Display.FLAG_SECURE) != 0) s.append("FLAG_SECURE ");
        if ((f & Display.FLAG_PRIVATE) != 0) s.append("PRIVATE ");
        if ((f & Display.FLAG_PRESENTATION) != 0) s.append("PRESENTATION ");
        if ((f & Display.FLAG_ROUND) != 0) s.append("ROUND ");
        return s.toString().trim();
    }

    private String stateText(int st) {
        switch (st) {
            case Display.STATE_OFF: return "OFF";
            case Display.STATE_ON: return "ON";
            case Display.STATE_DOZE: return "DOZE";
            case Display.STATE_DOZE_SUSPEND: return "DOZE_SUSPEND";
            case Display.STATE_ON_SUSPEND: return "ON_SUSPEND";
            case Display.STATE_VR: return "VR";
            default: return "UNKNOWN(" + st + ")";
        }
    }

    /* ---------- 往背屏投送 ---------- */
    private void showOnBackScreen() {
        dismissPres();
        Display[] ds = dm.getDisplays();
        Display target = null;
        for (Display d : ds) {
            if (d.getDisplayId() == Display.DEFAULT_DISPLAY) continue;
            if ((d.getFlags() & Display.FLAG_PRESENTATION) != 0) { target = d; break; }
            if (target == null) target = d;
        }
        if (target == null) {
            showFallback();
            return;
        }
        try {
            final Display t = target;
            backPres = new Presentation(this, t) {
                @Override protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    SnakeView sv = new SnakeView(MainActivity.this);
                    sv.setTiltSource(MainActivity.this);
                    FrameLayout fl = new FrameLayout(MainActivity.this);
                    fl.setBackgroundColor(0xFF08080C);
                    fl.addView(sv, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    setContentView(fl);
                }
            };
            backPres.show();
            appendLog("\n[已投送] 背屏 display " + t.getDisplayId() + " 上运行体感贪吃蛇");
        } catch (Throwable e) {
            appendLog("\n[投送失败] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            showFallback();
        }
    }

    private void showFallback() {
        if (mainSnake != null) return;
        try {
            ViewGroup root = (ViewGroup) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
            mainSnake = new SnakeView(this);
            mainSnake.setTiltSource(this);
            root.addView(mainSnake, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));
            appendLog("\n[退化模式] 未找到可用背屏显示器，游戏跑在主屏这张 View 上。");
        } catch (Throwable ignored) { }
    }

    private void appendLog(String s) {
        logView.setText(logView.getText() + s);
    }

    private void dismissPres() {
        if (backPres != null) {
            try { backPres.dismiss(); } catch (Throwable ignored) { }
            backPres = null;
        }
    }

    /* ---------- 传感器 ---------- */
    @Override
    protected void onResume() {
        super.onResume();
        List<Sensor> acc = sm.getSensorList(Sensor.TYPE_ACCELEROMETER);
        List<Sensor> gyro = sm.getSensorList(Sensor.TYPE_GYROSCOPE);
        hasAcc = !acc.isEmpty();
        hasGyro = !gyro.isEmpty();
        if (hasAcc) sm.registerListener(this, acc.get(0), SensorManager.SENSOR_DELAY_GAME);
        if (hasGyro) sm.registerListener(this, gyro.get(0), SensorManager.SENSOR_DELAY_GAME);
        probe();
        showOnBackScreen();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
        dismissPres();
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    public float getAx() { return ax; }
    public float getAy() { return ay; }
    public float getAz() { return az; }
    public float getGx() { return gx; }
    public float getGy() { return gy; }
}

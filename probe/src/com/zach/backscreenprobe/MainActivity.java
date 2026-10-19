package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
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
import java.util.List;

/**
 * 双路试探：
 * A) 公开 API —— LightsManager（点阵有时按灯效设备注册）
 * B) 隐藏服务 —— 对可达 binder 扫 transaction code，映射出实现了哪些方法
 */
public class MainActivity extends Activity {

    private static final String[][] SVCS = {
            {"stepos_device_control", "android.hardware.display.IStepOSDeviceControlService"},
            {"display_ext", "android.hardware.display.IStepOSDisplayManager"},
            {"stepos_statusbar", "stepos.statusbar.IStepOSStatusBarService"}
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
        title.setText("点阵 · 双路试探");
        title.setTextColor(0xFF4FF0C0);
        title.setTextSize(19);
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
        run.setText("重新试探");
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
          .append(" uid=").append(android.os.Process.myUid()).append(" ===\n\n");
        lightsProbe(sb);
        for (String[] s : SVCS) codeScan(sb, s[0], s[1]);
        append(sb.toString());
    }

    /* ---------- A: 公开 API 灯效 ---------- */
    private void lightsProbe(StringBuilder sb) {
        sb.append("[A] LightsManager\n");
        try {
            LightsManager lm = (LightsManager) getSystemService(LightsManager.class);
            if (lm == null) { sb.append("  service null\n\n"); return; }
            List<Light> lights = lm.getLights();
            sb.append("  light 数量: ").append(lights.size()).append("\n");
            for (Light l : lights) {
                sb.append("  · id=").append(l.getId())
                  .append(" name=").append(l.getName())
                  .append(" type=").append(l.getType())
                  .append(" brightnessCtrl=").append(l.hasBrightnessControl())
                  .append(" ordinal=").append(l.getOrdinal()).append("\n");
            }
            if (!lights.isEmpty()) {
                try {
                    Object session = lm.openSession();
                    sb.append("  openSession: OK\n");
                    java.lang.reflect.Method req = null;
                    try {
                        req = session.getClass().getMethod("requestLights", Light[].class, LightState.class);
                    } catch (Throwable ignored) { }
                    if (req == null) {
                        sb.append("  未找到 requestLights(Light[],LightState)，跳过写入\n");
                    } else {
                        for (Light l : lights) {
                            try {
                                LightState st = new LightState.Builder().setColor(0xFF00FF88).build();
                                req.invoke(session, new Light[]{l}, st);
                                sb.append("    set id=").append(l.getId()).append(" OK\n");
                            } catch (Throwable t) {
                                sb.append("    set id=").append(l.getId()).append(" -> ").append(simple(t)).append("\n");
                            }
                        }
                    }
                    try { session.getClass().getMethod("close").invoke(session); } catch (Throwable ignored) { }
                    sb.append("  session closed\n");
                } catch (Throwable t) {
                    sb.append("  openSession 失败: ").append(simple(t)).append("\n");
                }
            }
        } catch (Throwable t) {
            sb.append("  LightsManager 异常: ").append(simple(t)).append("\n");
        }
        sb.append("\n");
    }

    /* ---------- B: transaction code 扫描 ---------- */
    private void codeScan(StringBuilder sb, String svcName, String desc) {
        sb.append("[B] ").append(svcName).append(" (").append(desc).append(")\n");
        try {
            Class<?> smc = Class.forName("android.os.ServiceManager");
            Method get = smc.getMethod("getService", String.class);
            Object o = get.invoke(null, svcName);
            if (!(o instanceof IBinder)) { sb.append("  binder null\n\n"); return; }
            IBinder b = (IBinder) o;
            StringBuilder yes = new StringBuilder();
            int hit = 0;
            for (int code = 1; code <= 60; code++) {
                Parcel d = Parcel.obtain();
                Parcel r = Parcel.obtain();
                try {
                    d.writeInterfaceToken(desc);
                    boolean ok = b.transact(code, d, r, 0);
                    if (ok) {
                        hit++;
                        int first = -1, replySize = r.dataSize();
                        try {
                            r.setDataPosition(0);
                            if (replySize >= 4) first = r.readInt();
                        } catch (Throwable ignored) { }
                        yes.append("    code=").append(code)
                           .append(" true replySize=").append(replySize)
                           .append(" firstInt=").append(first).append("\n");
                    }
                } catch (Throwable t) {
                    yes.append("    code=").append(code).append(" 异常 ").append(simple(t)).append("\n");
                } finally {
                    d.recycle();
                    r.recycle();
                }
            }
            sb.append("  实现/响应的方法数: ").append(hit).append("\n").append(yes);
        } catch (Throwable t) {
            sb.append("  扫描失败: ").append(simple(t)).append("\n");
        }
        sb.append("\n");
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
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "dotmatrix_try.txt");
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

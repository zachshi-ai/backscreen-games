package com.zach.backscreenprobe;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * 点阵背屏直连尝试：反射拿 vendor.stepos.hardware.dot_matrix.IDotMatrix 的 binder，
 * 打印接口描述符，并按受控方式试写一帧。
 */
public class MainActivity extends Activity {

    private static final String SVC = "vendor.stepos.hardware.dot_matrix.IDotMatrix/default";
    private static final String TAG = "DotMatrixProbe";

    private TextView logView;
    private String report = "";
    private IBinder binder;
    private String descriptor = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08080C);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView title = new TextView(this);
        title.setText("点阵直连 · IDotMatrix");
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

        Button probe = new Button(this);
        probe.setText("1. 探测点阵服务");
        probe.setAllCaps(false);
        probe.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { probeService(); }
        });
        root.addView(probe);

        Button write = new Button(this);
        write.setText("2. 试写一帧（可能点亮点阵）");
        write.setAllCaps(false);
        write.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tryWrite(); }
        });
        root.addView(write);

        Button dump = new Button(this);
        dump.setText("3. 写入报告文件");
        dump.setAllCaps(false);
        dump.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dump(); toast("已写入 下载/backscreen_probe.txt"); }
        });
        root.addView(dump);

        TextView hint = new TextView(this);
        hint.setText("步骤 2 会向系统点阵服务发送测试帧，点阵可能亮起或闪一下；如果异常，重启手机即可恢复。");
        hint.setTextColor(0xFF6D6D7A);
        hint.setTextSize(11f);
        root.addView(hint);

        setContentView(root);
        probeService();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /* ---------- 1. 探服务 ---------- */
    private void probeService() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== IDotMatrix 服务探测 ===\n");
        sb.append("service: ").append(SVC).append("\n");
        try {
            Class<?> smc = Class.forName("android.os.ServiceManager");
            Method get = smc.getMethod("getService", String.class);
            Object o = get.invoke(null, SVC);
            sb.append("getService 返回: ").append(o == null ? "null" : o.getClass().getName()).append("\n");
            if (o instanceof IBinder) {
                binder = (IBinder) o;
                sb.append("binder alive=").append(binder.isBinderAlive()).append("\n");
                try {
                    descriptor = binder.getInterfaceDescriptor();
                    sb.append("interface descriptor: ").append(descriptor).append("\n");
                } catch (Throwable t) {
                    sb.append("descriptor 读取失败: ").append(t).append("\n");
                }
            } else {
                sb.append("不是 IBinder，无法直连。\n");
            }
        } catch (Throwable t) {
            sb.append("反射失败: ").append(t.getClass().getSimpleName()).append(" ")
              .append(String.valueOf(t.getMessage())).append("\n");
        }
        append(sb.toString());
    }

    /* ---------- 2. 试写 ---------- */
    private void tryWrite() {
        if (binder == null) { probeService(); }
        if (binder == null) { append("\n没有拿到 binder，跳过试写。\n"); return; }
        String desc = descriptor == null ? "" : descriptor;

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== 试写 ===\n");

        // 变体 A: token + 宽高 + 位图字节(128B, 32x32 全亮)
        sb.append(tryVariant(desc, 1, new byte[128], true));
        // 变体 B: token + 位图字节
        sb.append(tryVariant(desc, 2, new byte[128], false));
        // 变体 C: 64x32 全亮（256B）
        byte[] big = new byte[256];
        for (int i = 0; i < big.length; i++) big[i] = (byte) 0xFF;
        sb.append(tryVariantBig(desc, big));

        append(sb.toString());
    }

    private String tryVariant(String desc, int variant, byte[] payload, boolean withDims) {
        StringBuilder out = new StringBuilder();
        for (int code = 1; code <= 6; code++) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(desc);
                if (withDims) { data.writeInt(32); data.writeInt(32); }
                data.writeByteArray(payload);
                boolean ok = binder.transact(code, data, reply, 0);
                out.append("  变体").append(variant).append(" code=").append(code)
                   .append(" -> ok=").append(ok)
                   .append(" replySize=").append(reply.dataSize()).append("\n");
            } catch (Throwable t) {
                out.append("  变体").append(variant).append(" code=").append(code)
                   .append(" -> ").append(t.getClass().getSimpleName())
                   .append(": ").append(String.valueOf(t.getMessage())).append("\n");
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
        return out.toString();
    }

    private String tryVariantBig(String desc, byte[] payload) {
        StringBuilder out = new StringBuilder();
        for (int code = 1; code <= 4; code++) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(desc);
                data.writeInt(64);
                data.writeInt(32);
                data.writeByteArray(payload);
                boolean ok = binder.transact(code, data, reply, 0);
                out.append("  变体C(64x32) code=").append(code).append(" -> ok=").append(ok)
                   .append(" replySize=").append(reply.dataSize()).append("\n");
            } catch (Throwable t) {
                out.append("  变体C(64x32) code=").append(code).append(" -> ")
                   .append(t.getClass().getSimpleName()).append(": ")
                   .append(String.valueOf(t.getMessage())).append("\n");
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
        return out.toString();
    }

    private void append(String s) {
        Log.i(TAG, s);
        report = report + s;
        logView.setText(report);
        dump();
    }

    private void dump() {
        try {
            byte[] bytes = report.getBytes("UTF-8");
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
            }
        } catch (Throwable ignored) { }
    }

    private void toast(final String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}

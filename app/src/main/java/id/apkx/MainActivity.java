package id.apkx;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = 32;
        root.setPadding(p, p, p, p);

        status = new TextView(this);
        status.setTextSize(14f);
        root.addView(status);

        Button start = new Button(this);
        start.setText("Start server");
        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            startForegroundService(new Intent(this, ApkxService.class));
        });

        Button stop = new Button(this);
        stop.setText("Stop server");
        stop.setOnClickListener(v -> stopService(new Intent(this, ApkxService.class)));

        Button clear = new Button(this);
        clear.setText("Bersihkan workspace");
        clear.setOnClickListener(v -> Tools.clearWorkspace(this));

        root.addView(start);
        root.addView(stop);
        root.addView(clear);
        setContentView(root);
        handler.postDelayed(this::refresh, 400);
    }

    private void refresh() {
        status.setText("Endpoint: http://127.0.0.1:8080/mcp\nStatus: " + ApkxService.status()
                + "\n\n" + ApkxService.logTail());
        handler.postDelayed(this::refresh, 1000);
    }
}

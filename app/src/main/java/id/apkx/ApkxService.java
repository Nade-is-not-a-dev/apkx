package id.apkx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class ApkxService extends Service {
    private static volatile boolean running = false;
    private static final StringBuilder LOG = new StringBuilder();
    private static MiniHttp server;
    private static final int PORT = 8080;

    public static String status() { return running ? "running" : "stopped"; }

    public static synchronized String logTail() {
        int from = Math.max(0, LOG.length() - 1400);
        return LOG.substring(from);
    }

    public static synchronized void log(String line) {
        LOG.append(line).append('\n');
        if (LOG.length() > 8000) LOG.delete(0, LOG.length() - 8000);
        Log.i("apkx", line);
    }

    @Override public void onCreate() {
        super.onCreate();
        channel();
        startForeground(1, notification());
        new Thread(() -> {
            try {
                log("Memuat apktool.dex");
                ApkEngine.init(getApplicationContext());
                log("apktool.dex siap");
                Tools.init(getApplicationContext());
                server = new MiniHttp(PORT, McpHandler::handle);
                server.start();
                running = true;
                log("MCP siap: http://127.0.0.1:" + PORT + "/mcp");
            } catch (Throwable t) {
                log("Gagal start: " + t);
            }
        }, "apkx-init").start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        running = false;
        if (server != null) server.stopServer();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void channel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel("apkx", "apkx", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification() {
        Intent i = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, flags);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "apkx") : new Notification.Builder(this);
        return b.setContentTitle("apkx MCP")
                .setContentText("Server lokal berjalan")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .build();
    }
}

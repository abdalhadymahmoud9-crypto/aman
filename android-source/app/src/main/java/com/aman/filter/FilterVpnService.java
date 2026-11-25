package com.aman.filter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Local DNS filter. The tunnel carries ONLY DNS queries (a single /32 route
 * to the fake resolver address); all other traffic keeps its normal route,
 * so the filter cannot blackhole connectivity. Allowed queries are forwarded
 * to the upstream resolver through a protected (non-VPN) socket; blocked
 * names get an NXDOMAIN. Nothing is logged or sent anywhere else.
 */
public class FilterVpnService extends VpnService {
    /** True only while this process actually holds the tunnel. Dies with the process. */
    public static volatile boolean IS_RUNNING = false;

    public static final String ACTION_START = "com.aman.filter.START";
    public static final String ACTION_STOP = "com.aman.filter.STOP";
    public static final String ACTION_RELOAD = "com.aman.filter.RELOAD";

    private static final String TUN_ADDR = "10.0.0.1";
    private static final String FAKE_DNS = "10.0.0.2";
    private static final String UPSTREAM = "1.1.1.1";

    private ParcelFileDescriptor tun;
    private Thread worker;
    private volatile boolean running = false;
    private Blocklist blocklist;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopFilter();
            return START_NOT_STICKY;
        }
        if (ACTION_RELOAD.equals(action)) {
            if (blocklist != null) blocklist.reloadCustom();
            return START_STICKY;
        }
        startFilter();
        return START_STICKY;
    }

    private synchronized void startFilter() {
        if (running) return;
        try {
            blocklist = new Blocklist(this);
            blocklist.load();
        } catch (Exception e) {
            setRunningPref(false);
            stopSelf();
            return;
        }
        Builder b = new Builder()
                .setSession("Aman")
                .addAddress(TUN_ADDR, 32)
                .addRoute(FAKE_DNS, 32)       // only DNS queries enter the tunnel
                .addDnsServer(FAKE_DNS);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.setConfigureIntent(pi);
        try {
            tun = b.establish();
        } catch (Exception e) {
            tun = null;
        }
        if (tun == null) {           // permission revoked or another VPN owns it
            setRunningPref(false);
            stopSelf();
            return;
        }
        running = true;
        IS_RUNNING = true;
        setRunningPref(true);
        getSharedPreferences("stats", MODE_PRIVATE).edit()
                .putLong("heartbeat", android.os.SystemClock.elapsedRealtime()).apply();
        getSharedPreferences("stats", MODE_PRIVATE).edit()
                .putString("vpn_since", java.time.LocalDate.now().toString()).apply();
        startForegroundNotification();
        worker = new Thread(this::loop, "aman-dns");
        worker.start();
    }

    private void startForegroundNotification() {
        String chId = "filter";
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                chId, "Filtering status", NotificationManager.IMPORTANCE_MIN));
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, chId)
                .setContentTitle("Aman is filtering")
                .setContentText("Tap to pause or manage your lists")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    private void loop() {
        try (FileInputStream in = new FileInputStream(tun.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(tun.getFileDescriptor())) {
            InetAddress upstream = InetAddress.getByName(UPSTREAM);
            DatagramSocket socket = new DatagramSocket();
            protect(socket);                       // upstream queries bypass the tunnel
            socket.setSoTimeout(3000);
            byte[] buf = new byte[4096];
            while (running) {
                int len;
                try {
                    len = in.read(buf);
                } catch (Exception e) {
                    break;                           // tunnel closed
                }
                if (len <= 0) continue;
                getSharedPreferences("stats", MODE_PRIVATE).edit()
                        .putLong("heartbeat", android.os.SystemClock.elapsedRealtime()).apply();
                byte[] pkt = new byte[len];
                System.arraycopy(buf, 0, pkt, 0, len);
                DnsFilter.Query q = DnsFilter.parse(pkt, len);
                if (q == null) continue;             // not a plain DNS query: ignore
                bump("queries");
                try {
                    if (blocklist.isBlocked(q.qname)) {
                        bump("blocked");
                        out.write(DnsFilter.nxdomain(pkt, len, q));
                        out.flush();
                    } else {
                        int dnsOff = ((pkt[0] & 0xF) * 4) + 8;
                        byte[] payload = new byte[len - dnsOff];
                        System.arraycopy(pkt, dnsOff, payload, 0, payload.length);
                        byte[] resp = DnsFilter.forward(socket, payload, upstream, 53);
                        out.write(DnsFilter.wrapResponse(pkt, resp));
                        out.flush();
                    }
                } catch (Exception e) {
                    // one query failed (timeout, malformed): skip it, keep filtering
                }
            }
            socket.close();
        } catch (Exception ignored) {
        }
        stopFilter();
    }

    private synchronized void stopFilter() {
        running = false;
        IS_RUNNING = false;
        setRunningPref(false);
        if (worker != null) { worker.interrupt(); worker = null; }
        if (tun != null) {
            try { tun.close(); } catch (Exception ignored) {}
            tun = null;
        }
        stopForeground(true);
        stopSelf();
    }

    private void bump(String key) {
        SharedPreferences p = getSharedPreferences("stats", MODE_PRIVATE);
        p.edit().putLong(key, p.getLong(key, 0) + 1).apply();
    }

    private void setRunningPref(boolean v) {
        SharedPreferences.Editor e = getSharedPreferences("stats", MODE_PRIVATE).edit()
                .putBoolean("running", v);
        if (!v) e.remove("vpn_since");   // streak is grounded in actual VPN uptime
        e.apply();
    }

    @Override
    public void onDestroy() {
        stopFilter();
        super.onDestroy();
    }
}

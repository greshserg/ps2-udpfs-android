package com.greshserg.ps2udpfs;

import android.app.*;
import android.content.*;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.concurrent.*;
import java.util.regex.*;

public class ServerService extends Service {
    public static final String ACTION_STATUS = "com.greshserg.ps2udpfs.STATUS";
    public static final String ACTION_START = "com.greshserg.ps2udpfs.START";
    public static final String ACTION_STOP = "com.greshserg.ps2udpfs.STOP";

    private static final String PREFS = "udpfs_service_state";
    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_ROOT = "root";
    private static final String CHANNEL_ID = "ps2_udpfs";
    private static final int NOTIFICATION_ID = 1001;

    private enum State { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

    private final Object stateLock = new Object();
    private ExecutorService commands;
    private volatile java.lang.Process process;
    private volatile State state = State.STOPPED;
    private volatile boolean desiredRunning = false;
    private volatile boolean destroyed = false;
    private volatile String activeRoot = "";
    private volatile String boundIp = "";
    private volatile long generation = 0;
    private int restartBudget = 1;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;
    private String wifiLockMode = "HIGH_PERF";

    private static final Pattern PEER = Pattern.compile("\\[((?:\\d{1,3}\\.){3}\\d{1,3})(?::\\d+)?\\]");

    @Override public void onCreate() {
        super.onCreate();
        LogStore.begin(this);
        LogStore.append("Session: " + new java.util.Date() + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL + " Android " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT);
        commands = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "udpfs-control");
            t.setDaemon(true);
            return t;
        });

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "PS2 UDPFS", NotificationManager.IMPORTANCE_LOW));
        }
        startForeground(NOTIFICATION_ID, buildNotification("Подготовка сервера..."));

        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PS2Udpfs:Server");
        wakeLock.setReferenceCounted(false);

        WifiManager wifi = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            // LOW_LATENCY is only active while the screen is on and the app is
            // foreground. UDPFS must keep receiving ACK/NACK packets after the
            // phone is locked, so deliberately use HIGH_PERF on every API level.
            // The constant is deprecated on API 34, but remains the only public
            // Wi-Fi lock whose activation is not tied to screen/foreground state.
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PS2Udpfs:Streaming");
            wifiLock.setReferenceCounted(false);
            multicastLock = wifi.createMulticastLock("PS2Udpfs:Discovery");
            multicastLock.setReferenceCounted(false);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        final String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            desiredRunning = false;
            prefs.edit().putBoolean(PREF_ENABLED, false).apply();
            submitCommand(() -> {
                stopProcessInternal(true);
                stopSelfResult(startId);
            });
            return START_STICKY;
        }

        String root = intent == null ? null : intent.getStringExtra("root");
        boolean explicitStart = ACTION_START.equals(action) || (intent != null && root != null);

        if (explicitStart) {
            if (root != null && !root.trim().isEmpty()) {
                root = root.trim();
                prefs.edit().putString(PREF_ROOT, root).putBoolean(PREF_ENABLED, true).apply();
            }
            desiredRunning = true;
            restartBudget = 1;
        } else {
            desiredRunning = prefs.getBoolean(PREF_ENABLED, false);
        }

        if (root == null || root.trim().isEmpty()) root = prefs.getString(PREF_ROOT, "");
        final String requestedRoot = root == null ? "" : root.trim();

        if (desiredRunning && !requestedRoot.isEmpty()) {
            submitCommand(() -> startProcessInternal(requestedRoot, explicitStart));
        } else if (!desiredRunning) {
            submitCommand(() -> {
                stopProcessInternal(true);
                stopSelfResult(startId);
            });
        } else {
            sendStatus("ОШИБКА ЗАПУСКА udpfsd\nПапка игр не указана");
        }
        return START_STICKY;
    }

    private void submitCommand(Runnable r) {
        ExecutorService e = commands;
        if (destroyed || e == null || e.isShutdown()) return;
        try { e.execute(r); } catch (RejectedExecutionException ignored) {}
    }

    private Notification buildNotification(String text) {
        Notification.Builder n = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return n.setContentTitle("PS2 UDPFS Server")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void acquireRuntimeLocks() throws IOException {
        try {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
        } catch (Throwable e) {
            throw new IOException("Не удалось получить WakeLock", e);
        }
        try {
            if (wifiLock == null) throw new IOException("Wi-Fi Lock недоступен");
            if (!wifiLock.isHeld()) wifiLock.acquire();
        } catch (Exception e) {
            releaseRuntimeLocks();
            throw new IOException("Не удалось получить Wi-Fi Lock", e);
        }
        try {
            if (multicastLock != null && !multicastLock.isHeld()) multicastLock.acquire();
        } catch (Throwable e) {
            releaseRuntimeLocks();
            throw new IOException("Не удалось получить Wi-Fi MulticastLock", e);
        }
    }

    private void releaseRuntimeLocks() {
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); } catch (Throwable ignored) {}
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
    }

    private String powerStatus() {
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        String info = "\nWakeLock: " + (wakeLock != null && wakeLock.isHeld())
                + "\nWi-Fi Lock held: " + (wifiLock != null && wifiLock.isHeld())
                + " (" + wifiLockMode + ")";
        if (pm != null) {
            info += "\nЭкран включён: " + pm.isInteractive()
                    + "\nЭнергосбережение: " + pm.isPowerSaveMode();
            if (Build.VERSION.SDK_INT >= 23) {
                info += "\nDoze: " + pm.isDeviceIdleMode()
                        + "\nИсключение оптимизации батареи: "
                        + pm.isIgnoringBatteryOptimizations(getPackageName());
            }
        }
        info += "\nWi-Fi HIGH_PERF: рассчитан на работу с выключенным экраном.";
        return info;
    }

    private String findWifiIpv4() throws IOException {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) throw new IOException("ConnectivityManager недоступен");

        Network active = cm.getActiveNetwork();
        String ip = ipv4ForWifiNetwork(cm, active);
        if (ip != null) return ip;

        for (Network network : cm.getAllNetworks()) {
            ip = ipv4ForWifiNetwork(cm, network);
            if (ip != null) return ip;
        }
        throw new IOException("IPv4 Wi-Fi не найден. Подключите телефон к Wi-Fi роутера.");
    }

    private String ipv4ForWifiNetwork(ConnectivityManager cm, Network network) {
        if (network == null) return null;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null;
        LinkProperties lp = cm.getLinkProperties(network);
        if (lp == null) return null;
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress addr = la.getAddress();
            if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                return addr.getHostAddress();
            }
        }
        return null;
    }

    private void startProcessInternal(String root, boolean userRequested) {
        if (destroyed || !desiredRunning) return;

        java.lang.Process current = process;
        if (current != null && current.isAlive()) {
            if (root.equals(activeRoot)) {
                sendStatus("udpfsd уже работает\nRoot: " + activeRoot + "\nBind: " + boundIp + "\nDiscovery UDP: 62966");
                return;
            }
            stopProcessInternal(false);
        }

        state = State.STARTING;
        sendStatus("Подготовка udpfsd...");

        try {
            File exe = new File(getApplicationInfo().nativeLibraryDir, "libudpfsd.so");
            if (!exe.exists()) throw new IOException("libudpfsd.so не найден: " + exe.getAbsolutePath());
            if (!exe.canExecute()) throw new IOException("libudpfsd.so не исполняемый: " + exe.getAbsolutePath());
            if (root == null || root.trim().isEmpty()) throw new IOException("Папка игр не указана");

            File gameRoot = new File(root);
            if (!gameRoot.exists()) throw new IOException("Папка не существует: " + root);
            if (!gameRoot.isDirectory()) throw new IOException("Это не папка: " + root);

            String wifiIp = findWifiIpv4();
            acquireRuntimeLocks();

            ProcessBuilder pb = new ProcessBuilder(
                    exe.getAbsolutePath(),
                    "-fsroot", root,
                    "-verbose",
                    "-bind", wifiIp
            );
            pb.redirectErrorStream(true);

            java.lang.Process p = pb.start();
            long gen;
            synchronized (stateLock) {
                process = p;
                activeRoot = root;
                boundIp = wifiIp;
                generation++;
                gen = generation;
                state = State.RUNNING;
            }

            updateNotification("Работает • " + wifiIp + ":62966");
            sendStatus("udpfsd ЗАПУЩЕН\nRoot: " + root + "\nBind: " + wifiIp + "\nDiscovery UDP: 62966\nMulticastLock: " + (multicastLock != null && multicastLock.isHeld()) + powerStatus());

            Thread logThread = new Thread(() -> readProcessLog(p, gen), "udpfs-log-" + gen);
            logThread.setDaemon(true);
            logThread.start();

            Thread exitThread = new Thread(() -> awaitProcessExit(p, gen), "udpfs-exit-" + gen);
            exitThread.setDaemon(true);
            exitThread.start();
        } catch (Throwable e) {
            state = State.FAILED;
            releaseRuntimeLocks();
            if (!destroyed) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                sendStatus("ОШИБКА ЗАПУСКА udpfsd\n" + sw.toString());
                updateNotification("Ошибка запуска");
            }
        }
    }

    private boolean isCurrent(java.lang.Process p, long gen) {
        return process == p && generation == gen;
    }

    private void readProcessLog(java.lang.Process p, long gen) {
        StringBuilder tail = new StringBuilder();
        String previousPower = "";
        long lastPowerCheck = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!isCurrent(p, gen)) break;
                long now = SystemClock.elapsedRealtime();
                if (now - lastPowerCheck >= 1000) {
                    String power = powerStatus();
                    if (!power.equals(previousPower)) {
                        LogStore.append(new java.util.Date() + " Power: " + power);
                        previousPower = power;
                    }
                    lastPowerCheck = now;
                }
                LogStore.append(line);
                tail.append(line).append('\n');
                if (tail.length() > 5000) tail.delete(0, tail.length() - 5000);
                sendPeerActivity(line);
                sendStatus("udpfsd работает\nBind: " + boundIp + powerStatus() + "\n--- log ---\n" + tail.toString());
            }
        } catch (IOException e) {
            if (isCurrent(p, gen) && state != State.STOPPING) {
                sendStatus("udpfsd: ошибка чтения лога\n" + e);
            }
        }
    }

    private void awaitProcessExit(java.lang.Process p, long gen) {
        int code;
        try {
            code = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        final int exitCode = code;
        submitCommand(() -> handleProcessExit(p, gen, exitCode));
    }

    private void handleProcessExit(java.lang.Process p, long gen, int code) {
        if (!isCurrent(p, gen)) return;

        synchronized (stateLock) {
            process = null;
            state = State.STOPPED;
        }
        releaseRuntimeLocks();

        if (!desiredRunning || destroyed) return;

        if (restartBudget > 0) {
            restartBudget--;
            sendStatus("udpfsd неожиданно завершился\nКод: " + code + "\nПовторный запуск через 1 сек...");
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            startProcessInternal(activeRoot, false);
        } else {
            state = State.FAILED;
            sendStatus("udpfsd ЗАВЕРШИЛСЯ\nКод: " + code + "\nАвтоперезапуск уже использован. Нажмите Запустить сервер.");
            updateNotification("Сервер остановлен с ошибкой");
        }
    }

    private void stopProcessInternal(boolean releaseLocks) {
        java.lang.Process p = process;
        if (p == null) {
            state = State.STOPPED;
            if (releaseLocks) releaseRuntimeLocks();
            return;
        }

        state = State.STOPPING;
        sendStatus("Останавливаю udpfsd...");
        try { p.destroy(); } catch (Throwable ignored) {}

        try {
            if (!p.waitFor(2000, TimeUnit.MILLISECONDS)) {
                try { p.destroyForcibly(); } catch (Throwable ignored) {}
                try { p.waitFor(1000, TimeUnit.MILLISECONDS); } catch (Throwable ignored) {}
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try { p.destroyForcibly(); } catch (Throwable ignored) {}
        }

        try { p.getInputStream().close(); } catch (Exception ignored) {}
        try { p.getErrorStream().close(); } catch (Exception ignored) {}
        try { p.getOutputStream().close(); } catch (Exception ignored) {}

        synchronized (stateLock) {
            if (process == p) process = null;
            state = State.STOPPED;
            generation++;
        }
        activeRoot = "";
        boundIp = "";
        if (releaseLocks) releaseRuntimeLocks();
        updateNotification("Сервер остановлен");
        sendStatus("Сервер остановлен");
    }

    private void sendStatus(String text) {
        if (!text.contains("--- log ---")) {
            LogStore.append(new java.util.Date() + " " + text);
            LogStore.flush();
        }
        String logError = LogStore.error();
        if (logError != null) text += "\nОшибка записи лога: " + logError;
        Intent i = new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra("text", text);
        sendBroadcast(i);
    }

    private void sendPeerActivity(String line) {
        Matcher m = PEER.matcher(line);
        if (!m.find()) return;
        Intent i = new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        i.putExtra("peer_activity", true);
        i.putExtra("peer_ip", m.group(1));
        sendBroadcast(i);
    }

    @Override public void onDestroy() {
        destroyed = true;
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService e = commands;
        if (e != null && !e.isShutdown()) {
            try {
                e.execute(() -> {
                    try { stopProcessInternal(true); }
                    finally { latch.countDown(); }
                });
                latch.await(2500, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {}
            e.shutdownNow();
        } else {
            releaseRuntimeLocks();
        }
        LogStore.flush();
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent i) { return null; }
}

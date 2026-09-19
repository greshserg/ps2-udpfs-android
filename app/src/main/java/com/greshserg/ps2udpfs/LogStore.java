package com.greshserg.ps2udpfs;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class LogStore {
    private static BufferedWriter writer;
    private static String error;
    private static long lastFlush;

    static synchronized void begin(Context c) {
        try {
            if (writer != null) { writer.close(); writer = null; }
            File dir = new File(c.getFilesDir(), "logs");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create log directory");
            File current = new File(dir, "udpfs-current.txt");
            File previous = new File(dir, "udpfs-previous.txt");
            if (current.exists()) {
                if (previous.exists() && !previous.delete()) throw new IOException("Cannot remove previous log");
                if (!current.renameTo(previous)) throw new IOException("Cannot preserve previous log");
            }
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(current), StandardCharsets.UTF_8));
            error = null;
        } catch (IOException e) { error = e.toString(); }
    }

    static synchronized void append(String text) {
        if (writer == null) return;
        try {
            writer.write(text);
            writer.newLine();
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastFlush >= 1000) { writer.flush(); lastFlush = now; }
        } catch (IOException e) { error = e.toString(); }
    }

    static synchronized void flush() {
        try { if (writer != null) writer.flush(); }
        catch (IOException e) { error = e.toString(); }
    }

    static synchronized String error() { return error; }

    static synchronized File snapshot(Context c) throws IOException {
        flush();
        if (error != null) throw new IOException(error);
        File source = new File(c.getFilesDir(), "logs/udpfs-current.txt");
        if (!source.isFile()) throw new IOException("Сначала запустите сервер");
        File dir = new File(c.getCacheDir(), "shared-logs");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Не удалось создать папку");
        File target = File.createTempFile("udpfs-", ".txt", dir);
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        return target;
    }
}

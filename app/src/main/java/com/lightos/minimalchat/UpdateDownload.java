package com.lightos.minimalchat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GitHub release APK downloader. Follows cross-host redirects by hand and
 * closes the connection so Android's bundled OkHttp does not die with
 * "unexpected end of stream" on the 302 to release-assets.githubusercontent.com.
 */
public final class UpdateDownload {
    public static final int MAX_REDIRECTS = 8;
    public static final long MIN_APK_BYTES = 1024;

    private UpdateDownload() {}

    public static void downloadApk(String startUrl, File dest) throws IOException {
        downloadApk(startUrl, dest, "lightui-android");
    }

    public static void downloadApk(String startUrl, File dest, String userAgent) throws IOException {
        if (startUrl == null || startUrl.trim().length() == 0) throw new IOException("no apk url");
        if (dest == null) throw new IOException("no dest");
        String url = startUrl.trim();
        File dir = dest.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IOException("couldn't create download dir");
        File tmp = new File(dest.getAbsolutePath() + ".part");
        if (tmp.exists() && !tmp.delete()) throw new IOException("couldn't clear partial download");

        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            boolean keep = false;
            try {
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(20000);
                c.setReadTimeout(60000);
                c.setUseCaches(false);
                c.setRequestProperty("User-Agent",
                        userAgent == null || userAgent.length() == 0 ? "lightui-android" : userAgent);
                c.setRequestProperty("Accept", "application/octet-stream,*/*");
                c.setRequestProperty("Accept-Encoding", "identity");
                c.setRequestProperty("Connection", "close");
                c.setRequestProperty("Cache-Control", "no-cache");
                int code = c.getResponseCode();
                if (ToolText.isHttpRedirect(code)) {
                    String next = ToolText.resolveRedirectUrl(url, c.getHeaderField("Location"));
                    if (next == null) throw new IOException("redirect with no location (" + code + ")");
                    url = next;
                    continue;
                }
                if (code != 200) throw new IOException("http " + code);
                long expected = contentLength(c);
                InputStream in = c.getInputStream();
                FileOutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[16384];
                byte[] magic = new byte[2];
                int magicGot = 0;
                long written = 0;
                try {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        if (n <= 0) continue;
                        if (magicGot < 2) {
                            int copy = Math.min(2 - magicGot, n);
                            System.arraycopy(buf, 0, magic, magicGot, copy);
                            magicGot += copy;
                            if (magicGot >= 2 && !ToolText.isApkMagic(magic, magicGot)) {
                                throw new IOException("not an apk (github sent html)");
                            }
                        }
                        out.write(buf, 0, n);
                        written += n;
                    }
                    out.flush();
                    out.getFD().sync();
                } finally {
                    try { out.close(); } catch (Exception ignored) {}
                    try { in.close(); } catch (Exception ignored) {}
                }
                if (!ToolText.isApkMagic(magic, magicGot)) throw new IOException("not an apk");
                if (ToolText.downloadLengthMismatch(expected, written)) {
                    throw new IOException("truncated download (" + written + "/" + expected + ")");
                }
                if (written < MIN_APK_BYTES) throw new IOException("download too small");
                if (dest.exists() && !dest.delete()) throw new IOException("couldn't replace apk");
                if (!tmp.renameTo(dest)) {
                    copyFile(tmp, dest);
                    if (!tmp.delete()) { /* leftover .part is harmless */ }
                }
                keep = true;
                return;
            } finally {
                try { c.disconnect(); } catch (Exception ignored) {}
                if (!keep && tmp.exists()) tmp.delete();
            }
        }
        throw new IOException("too many redirects");
    }

    static long contentLength(HttpURLConnection c) {
        if (c == null) return -1;
        try {
            String cl = c.getHeaderField("Content-Length");
            if (cl == null || cl.trim().length() == 0) return -1;
            return Long.parseLong(cl.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    static void copyFile(File from, File to) throws IOException {
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            out.getFD().sync();
        } finally {
            try { in.close(); } catch (Exception ignored) {}
            try { out.close(); } catch (Exception ignored) {}
        }
    }
}

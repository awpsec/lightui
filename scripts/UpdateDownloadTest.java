package com.lightos.minimalchat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class UpdateDownloadTest {
    private static int failed = 0;

    private static void assertTrue(String name, boolean v) {
        if (!v) { System.err.println("FAIL " + name); failed++; }
        else System.out.println("ok   " + name);
    }

    public static void main(String[] args) throws Exception {
        File dest = new File("/tmp/lightui-update-download-test.apk");
        if (dest.exists()) dest.delete();

        RedirectServer redirect = new RedirectServer(fakeApk(2048));
        redirect.start();
        try {
            UpdateDownload.downloadApk("http://127.0.0.1:" + redirect.port + "/old", dest);
            assertTrue("redirect download size", dest.length() == 2048);
            byte[] head = new byte[2];
            java.io.FileInputStream in = new java.io.FileInputStream(dest);
            try { assertTrue("redirect magic read", in.read(head) == 2); }
            finally { in.close(); }
            assertTrue("redirect apk magic", ToolText.isApkMagic(head));
        } finally {
            redirect.close();
        }

        dest.delete();
        TruncateServer trunc = new TruncateServer();
        trunc.start();
        try {
            boolean threw = false;
            try {
                UpdateDownload.downloadApk("http://127.0.0.1:" + trunc.port + "/apk", dest);
            } catch (Exception e) {
                threw = ToolText.isTransientDownloadError(e) || (e.getMessage() != null && e.getMessage().contains("truncated"));
                if (!threw) System.err.println("unexpected trunc error: " + e);
            }
            assertTrue("truncated download rejected", threw);
            assertTrue("truncated file cleaned", !dest.exists());
        } finally {
            trunc.close();
        }

        HtmlServer html = new HtmlServer();
        html.start();
        try {
            boolean threw = false;
            try {
                UpdateDownload.downloadApk("http://127.0.0.1:" + html.port + "/apk", dest);
            } catch (Exception e) {
                threw = e.getMessage() != null && e.getMessage().toLowerCase().contains("not an apk");
                if (!threw) System.err.println("unexpected html error: " + e);
            }
            assertTrue("html rejected", threw);
            assertTrue("html file cleaned", !dest.exists());
        } finally {
            html.close();
        }

        try {
            File live = new File("/tmp/lightui-github-apk-test.apk");
            if (live.exists()) live.delete();
            UpdateDownload.downloadApk(
                    "https://github.com/awpsec/lightui/releases/download/v1.0.33/lightui-release.apk",
                    live);
            assertTrue("github apk size", live.length() > 50000);
            byte[] head = new byte[2];
            java.io.FileInputStream in = new java.io.FileInputStream(live);
            try { assertTrue("github magic read", in.read(head) == 2); }
            finally { in.close(); }
            assertTrue("github apk magic", ToolText.isApkMagic(head));
            live.delete();
        } catch (Exception e) {
            System.err.println("FAIL github live download: " + e);
            failed++;
        }

        if (failed > 0) { System.err.println(failed + " failed"); System.exit(1); }
        System.out.println("all update download tests passed");
    }

    private static byte[] fakeApk(int n) {
        byte[] body = new byte[n];
        body[0] = 'P';
        body[1] = 'K';
        body[2] = 3;
        body[3] = 4;
        for (int i = 4; i < n; i++) body[i] = (byte) (i & 0xff);
        return body;
    }

    private static void drainRequest(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            byte[] a = buf.toByteArray();
            if (a.length >= 4
                    && a[a.length - 4] == '\r' && a[a.length - 3] == '\n'
                    && a[a.length - 2] == '\r' && a[a.length - 1] == '\n') break;
        }
    }

    private static void writeAll(Socket s, String headers, byte[] body) throws Exception {
        OutputStream out = s.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        if (body != null && body.length > 0) out.write(body);
        out.flush();
    }

    static final class RedirectServer extends Thread {
        final ServerSocket ss;
        final int port;
        final byte[] body;
        volatile boolean running = true;

        RedirectServer(byte[] body) throws Exception {
            ss = new ServerSocket(0);
            port = ss.getLocalPort();
            this.body = body;
            setDaemon(true);
        }

        @Override public void run() {
            try {
                Socket first = ss.accept();
                drainRequest(first);
                writeAll(first,
                        "HTTP/1.1 302 Found\r\n"
                                + "Location: http://127.0.0.1:" + port + "/apk\r\n"
                                + "Content-Length: 0\r\n"
                                + "Connection: close\r\n\r\n",
                        null);
                first.close();
                Socket second = ss.accept();
                drainRequest(second);
                writeAll(second,
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: application/vnd.android.package-archive\r\n"
                                + "Content-Length: " + body.length + "\r\n"
                                + "Connection: close\r\n\r\n",
                        body);
                second.close();
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }

        void close() {
            running = false;
            try { ss.close(); } catch (Exception ignored) {}
        }
    }

    static final class TruncateServer extends Thread {
        final ServerSocket ss;
        final int port;
        volatile boolean running = true;

        TruncateServer() throws Exception {
            ss = new ServerSocket(0);
            port = ss.getLocalPort();
            setDaemon(true);
        }

        @Override public void run() {
            try {
                Socket s = ss.accept();
                drainRequest(s);
                byte[] body = fakeApk(80);
                writeAll(s,
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: application/octet-stream\r\n"
                                + "Content-Length: 2048\r\n"
                                + "Connection: close\r\n\r\n",
                        body);
                s.close();
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }

        void close() {
            running = false;
            try { ss.close(); } catch (Exception ignored) {}
        }
    }

    static final class HtmlServer extends Thread {
        final ServerSocket ss;
        final int port;
        volatile boolean running = true;

        HtmlServer() throws Exception {
            ss = new ServerSocket(0);
            port = ss.getLocalPort();
            setDaemon(true);
        }

        @Override public void run() {
            try {
                Socket s = ss.accept();
                drainRequest(s);
                byte[] body = "<!DOCTYPE html><html>not an apk</html>".getBytes(StandardCharsets.US_ASCII);
                writeAll(s,
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: text/html\r\n"
                                + "Content-Length: " + body.length + "\r\n"
                                + "Connection: close\r\n\r\n",
                        body);
                s.close();
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }

        void close() {
            running = false;
            try { ss.close(); } catch (Exception ignored) {}
        }
    }
}

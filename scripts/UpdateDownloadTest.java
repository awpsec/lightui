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
        assertTrue("wildcard cdn", UpdateDownload.dnsMatches("release-assets.githubusercontent.com", "*.githubusercontent.com"));
        assertTrue("exact host", UpdateDownload.dnsMatches("github.com", "github.com"));
        assertTrue("wildcard does not match apex", !UpdateDownload.dnsMatches("github.com", "*.github.com"));

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
        ResumeServer resume = new ResumeServer(fakeApk(2048));
        resume.start();
        try {
            UpdateDownload.downloadApk("http://127.0.0.1:" + resume.port + "/apk", dest);
            assertTrue("resume completed size", dest.length() == 2048);
            assertTrue("resume is usable", UpdateDownload.isUsableApk(dest, 2048));
        } finally {
            resume.close();
        }

        dest.delete();
        KeepAliveServer keep = new KeepAliveServer(fakeApk(2048));
        keep.start();
        try {
            long t0 = System.currentTimeMillis();
            UpdateDownload.downloadApk("http://127.0.0.1:" + keep.port + "/apk", dest);
            long elapsed = System.currentTimeMillis() - t0;
            assertTrue("keepalive size", dest.length() == 2048);
            assertTrue("keepalive did not wait for eof", elapsed < 4000);
        } finally {
            keep.close();
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
                    "https://github.com/awpsec/lightui/releases/download/v1.0.36/lightui-release.apk",
                    live);
            assertTrue("github apk size", live.length() > 50000);
            byte[] head = new byte[2];
            java.io.FileInputStream in = new java.io.FileInputStream(live);
            try { assertTrue("github magic read", in.read(head) == 2); }
            finally { in.close(); }
            assertTrue("github apk magic", ToolText.isApkMagic(head));
            assertTrue("github complete zip", UpdateDownload.isUsableApk(live, -1));
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
        if (n >= 22) {
            int i = n - 22;
            body[i] = 'P';
            body[i + 1] = 'K';
            body[i + 2] = 5;
            body[i + 3] = 6;
        }
        for (int i = 4; i < n - 22; i++) body[i] = (byte) (i & 0xff);
        return body;
    }

    private static String drainRequest(Socket s) throws Exception {
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
        return new String(buf.toByteArray(), StandardCharsets.US_ASCII);
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
                byte[] full = fakeApk(2048);
                byte[] body = new byte[80];
                System.arraycopy(full, 0, body, 0, 80);
                writeAll(s,
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: application/octet-stream\r\n"
                                + "Content-Length: 2048\r\n"
                                + "Connection: close\r\n\r\n",
                        body);
                s.close();
            } catch (Exception e) {
                if (running) e.printStackTrace();
            } finally {
                running = false;
                try { ss.close(); } catch (Exception ignored) {}
            }
        }

        void close() {
            running = false;
            try { ss.close(); } catch (Exception ignored) {}
        }
    }

    static final class ResumeServer extends Thread {
        final ServerSocket ss;
        final int port;
        final byte[] body;
        volatile boolean running = true;

        ResumeServer(byte[] body) throws Exception {
            ss = new ServerSocket(0);
            port = ss.getLocalPort();
            this.body = body;
            setDaemon(true);
        }

        @Override public void run() {
            try {
                Socket first = ss.accept();
                drainRequest(first);
                byte[] head = new byte[80];
                System.arraycopy(body, 0, head, 0, 80);
                writeAll(first,
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: application/octet-stream\r\n"
                                + "Content-Length: " + body.length + "\r\n"
                                + "Accept-Ranges: bytes\r\n"
                                + "Connection: close\r\n\r\n",
                        head);
                first.close();
                Socket second = ss.accept();
                String req = drainRequest(second);
                int start = 80;
                int rangeAt = req.toLowerCase().indexOf("range: bytes=");
                if (rangeAt >= 0) {
                    String rest = req.substring(rangeAt + "range: bytes=".length());
                    int dash = rest.indexOf('-');
                    if (dash > 0) {
                        try { start = Integer.parseInt(rest.substring(0, dash).trim()); }
                        catch (Exception ignored) {}
                    }
                }
                if (start < 0 || start > body.length) start = 80;
                byte[] part = new byte[body.length - start];
                System.arraycopy(body, start, part, 0, part.length);
                writeAll(second,
                        "HTTP/1.1 206 Partial Content\r\n"
                                + "Content-Type: application/octet-stream\r\n"
                                + "Content-Range: bytes " + start + "-" + (body.length - 1) + "/" + body.length + "\r\n"
                                + "Content-Length: " + part.length + "\r\n"
                                + "Connection: close\r\n\r\n",
                        part);
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

    static final class KeepAliveServer extends Thread {
        final ServerSocket ss;
        final int port;
        final byte[] body;
        volatile boolean running = true;

        KeepAliveServer(byte[] body) throws Exception {
            ss = new ServerSocket(0);
            port = ss.getLocalPort();
            this.body = body;
            setDaemon(true);
        }

        @Override public void run() {
            Socket s = null;
            try {
                s = ss.accept();
                drainRequest(s);
                writeAll(s,
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: application/vnd.android.package-archive\r\n"
                                + "Content-Length: " + body.length + "\r\n"
                                + "Connection: keep-alive\r\n\r\n",
                        body);
                try { Thread.sleep(15000); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                if (running) e.printStackTrace();
            } finally {
                if (s != null) try { s.close(); } catch (Exception ignored) {}
            }
        }

        void close() {
            running = false;
            interrupt();
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
                while (running) {
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
                }
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

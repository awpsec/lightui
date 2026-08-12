package com.lightos.minimalchat;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * In-app APK downloader. GitHub 302s to Azure blobs over HTTP/2;
 * Android's bundled OkHttp often dies there with "unexpected end of stream".
 * HTTPS downloads speak HTTP/1.1, stop at Content-Length (Azure keep-alive
 * does not close), and resume with Range.
 */
public final class UpdateDownload {
    public static final int MAX_REDIRECTS = 8;
    public static final int MAX_ATTEMPTS = 8;
    public static final long MIN_APK_BYTES = 1024;

    private UpdateDownload() {}

    public static void downloadApk(String startUrl, File dest) throws IOException {
        downloadApk(startUrl, dest, "lightui-android");
    }

    public static void downloadApk(String startUrl, File dest, String userAgent) throws IOException {
        if (startUrl == null || startUrl.trim().length() == 0) throw new IOException("no apk url");
        if (dest == null) throw new IOException("no dest");
        String ua = userAgent == null || userAgent.length() == 0 ? "lightui-android" : userAgent;
        File dir = dest.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IOException("couldn't create download dir");
        File tmp = new File(dest.getAbsolutePath() + ".part");
        String current = startUrl.trim();
        String origin = current;
        IOException last = null;
        int redirects = 0;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; ) {
            try {
                Hop hop = fetch(current, tmp, ua);
                if (hop.redirectTo != null) {
                    if (++redirects > MAX_REDIRECTS) throw new IOException("too many redirects");
                    current = hop.redirectTo;
                    continue;
                }
                redirects = 0;
                if (isUsableApk(tmp, hop.totalSize)) {
                    moveTo(tmp, dest);
                    return;
                }
                last = new IOException("truncated download (" + tmp.length() + "/" + hop.totalSize + ")");
            } catch (IOException e) {
                if (isUsableApk(tmp, -1)) {
                    moveTo(tmp, dest);
                    return;
                }
                last = e;
                int code = httpCode(e);
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.US);
                if (code == 401 || code == 403 || code == 404 || msg.contains("not an apk")) {
                    current = origin;
                    if (tmp.exists()) tmp.delete();
                } else if (code >= 400 && code < 500 && code != 416) {
                    if (tmp.exists()) tmp.delete();
                    throw e;
                }
            }
            attempt++;
            if (attempt <= MAX_ATTEMPTS) {
                try { Thread.sleep(250L * attempt); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        if (isUsableApk(tmp, -1)) {
            moveTo(tmp, dest);
            return;
        }
        if (tmp.exists()) tmp.delete();
        throw last != null ? last : new IOException("download failed");
    }

    static final class Hop {
        String redirectTo;
        long totalSize = -1;
    }

    static Hop fetch(String url, File tmp, String ua) throws IOException {
        if (url.startsWith("https://")) return fetchHttps11(url, tmp, ua);
        return fetchUrlConnection(url, tmp, ua);
    }

    static Hop fetchUrlConnection(String url, File tmp, String ua) throws IOException {
        long have = tmp.exists() ? tmp.length() : 0;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setUseCaches(false);
            applyHeaders(c, ua, have);
            int code = c.getResponseCode();
            Hop hop = new Hop();
            if (ToolText.isHttpRedirect(code)) {
                hop.redirectTo = ToolText.resolveRedirectUrl(url, c.getHeaderField("Location"));
                if (hop.redirectTo == null) throw new IOException("redirect with no location (" + code + ")");
                return hop;
            }
            have = prepareBody(code, tmp, have, c.getHeaderField("Content-Range"));
            hop.totalSize = totalSize(c.getHeaderField("Content-Range"), contentLength(c), have);
            appendStream(c.getInputStream(), tmp, have == 0, contentLength(c));
            return hop;
        } finally {
            try { c.disconnect(); } catch (Exception ignored) {}
        }
    }

    static Hop fetchHttps11(String url, File tmp, String ua) throws IOException {
        URL parsed = new URL(url);
        String host = parsed.getHost();
        int port = parsed.getPort() > 0 ? parsed.getPort() : 443;
        String path = parsed.getFile();
        if (path == null || path.length() == 0) path = "/";
        long have = tmp.exists() ? tmp.length() : 0;
        Socket raw = new Socket();
        SSLSocket socket = null;
        try {
            raw.connect(new InetSocketAddress(host, port), 20000);
            raw.setSoTimeout(60000);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = (SSLSocket) factory.createSocket(raw, host, port, true);
            forceHttp11(socket);
            socket.setSoTimeout(60000);
            socket.startHandshake();
            verifyHost(host, socket.getSession());
            StringBuilder req = new StringBuilder();
            req.append("GET ").append(path).append(" HTTP/1.1\r\n");
            req.append("Host: ").append(host).append("\r\n");
            req.append("User-Agent: ").append(ua).append("\r\n");
            req.append("Accept: application/octet-stream,*/*\r\n");
            req.append("Accept-Encoding: identity\r\n");
            req.append("Cache-Control: no-cache\r\n");
            req.append("Connection: close\r\n");
            if (have > 0) req.append("Range: bytes=").append(have).append("-\r\n");
            req.append("\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            Headers headers = readHeaders(in);
            Hop hop = new Hop();
            if (ToolText.isHttpRedirect(headers.code)) {
                hop.redirectTo = ToolText.resolveRedirectUrl(url, headers.location);
                if (hop.redirectTo == null) throw new IOException("redirect with no location (" + headers.code + ")");
                return hop;
            }
            have = prepareBody(headers.code, tmp, have, headers.contentRange);
            hop.totalSize = totalSize(headers.contentRange, headers.contentLength, have);
            if (headers.chunked) appendChunked(in, tmp, have == 0);
            else appendStream(in, tmp, have == 0, headers.contentLength);
            return hop;
        } finally {
            if (socket != null) try { socket.close(); } catch (Exception ignored) {}
            else try { raw.close(); } catch (Exception ignored) {}
        }
    }

    static void applyHeaders(HttpURLConnection c, String ua, long have) {
        c.setRequestProperty("User-Agent", ua);
        c.setRequestProperty("Accept", "application/octet-stream,*/*");
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("Connection", "close");
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
    }

    static long prepareBody(int code, File tmp, long have, String contentRange) throws IOException {
        if (code == 416) {
            if (tmp.exists()) tmp.delete();
            throw new IOException("http 416");
        }
        if (code != 200 && code != 206) throw new IOException("http " + code);
        if (code == 200 && have > 0) {
            have = 0;
            if (tmp.exists() && !tmp.delete()) throw new IOException("couldn't reset partial");
        }
        if (code == 206) {
            long start = parseContentRangeStart(contentRange);
            if (start >= 0 && start != have) {
                if (tmp.exists()) tmp.delete();
                throw new IOException("range mismatch");
            }
        }
        return have;
    }

    static void forceHttp11(SSLSocket socket) {
        try {
            SSLParameters p = socket.getSSLParameters();
            SSLParameters.class.getMethod("setApplicationProtocols", String[].class)
                    .invoke(p, new Object[] { new String[] { "http/1.1" } });
            socket.setSSLParameters(p);
        } catch (Exception ignored) { }
    }

    static void verifyHost(String host, javax.net.ssl.SSLSession session) throws IOException {
        if (host == null || host.length() == 0 || session == null) throw new IOException("tls host mismatch");
        try {
            Certificate[] chain = session.getPeerCertificates();
            if (chain == null || chain.length == 0) throw new IOException("tls host mismatch");
            if (hostMatchesCert(host, (X509Certificate) chain[0])) return;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("tls host mismatch");
        }
        throw new IOException("tls host mismatch");
    }

    static boolean hostMatchesCert(String host, X509Certificate cert) {
        if (host == null || cert == null) return false;
        String h = host.toLowerCase(Locale.US);
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans != null) {
                boolean sawDns = false;
                for (List<?> san : sans) {
                    if (san == null || san.size() < 2) continue;
                    Object type = san.get(0);
                    Object value = san.get(1);
                    if (!(type instanceof Integer) || ((Integer) type).intValue() != 2 || !(value instanceof String)) continue;
                    sawDns = true;
                    if (dnsMatches(h, ((String) value).toLowerCase(Locale.US))) return true;
                }
                if (sawDns) return false;
            }
        } catch (Exception ignored) {}
        String dn = cert.getSubjectX500Principal() == null ? "" : cert.getSubjectX500Principal().getName();
        String cn = cnFromDn(dn);
        return cn.length() > 0 && dnsMatches(h, cn.toLowerCase(Locale.US));
    }

    static String cnFromDn(String dn) {
        if (dn == null) return "";
        String[] parts = dn.split(",");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.length() >= 3 && p.regionMatches(true, 0, "CN=", 0, 3)) return p.substring(3).trim();
        }
        return "";
    }

    static boolean dnsMatches(String host, String pattern) {
        if (host == null || pattern == null) return false;
        if (pattern.startsWith("*.")) {
            String rest = pattern.substring(2);
            int dot = host.indexOf('.');
            return rest.length() > 0 && dot > 0 && host.substring(dot + 1).equals(rest);
        }
        return host.equals(pattern);
    }

    static final class Headers {
        int code;
        String location = "";
        String contentRange = "";
        long contentLength = -1;
        boolean chunked;
    }

    static Headers readHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        int state = 0;
        int b;
        while ((b = in.read()) != -1) {
            raw.write(b);
            if (raw.size() > 65536) throw new IOException("headers too large");
            if (state == 0 && b == '\r') state = 1;
            else if (state == 1 && b == '\n') state = 2;
            else if (state == 2 && b == '\r') state = 3;
            else if (state == 3 && b == '\n') break;
            else if (b == '\r') state = 1;
            else state = 0;
        }
        if (raw.size() < 8) throw new IOException("empty response");
        String text = new String(raw.toByteArray(), StandardCharsets.US_ASCII);
        String[] lines = text.split("\r\n");
        Headers h = new Headers();
        if (lines.length == 0 || !lines[0].startsWith("HTTP/")) throw new IOException("bad status");
        String[] status = lines[0].split(" ");
        if (status.length < 2) throw new IOException("bad status");
        try { h.code = Integer.parseInt(status[1].trim()); }
        catch (Exception e) { throw new IOException("bad status"); }
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.US);
            String value = lines[i].substring(colon + 1).trim();
            if ("location".equals(name)) h.location = value;
            else if ("content-length".equals(name)) {
                try { h.contentLength = Long.parseLong(value); } catch (Exception ignored) {}
            } else if ("content-range".equals(name)) h.contentRange = value;
            else if ("transfer-encoding".equals(name) && value.toLowerCase(Locale.US).contains("chunked")) h.chunked = true;
        }
        return h;
    }

    static void appendStream(InputStream in, File tmp, boolean replace, long expectedThisHop) throws IOException {
        long already = replace ? 0 : tmp.length();
        FileOutputStream out = new FileOutputStream(tmp, !replace);
        byte[] buf = new byte[16384];
        byte[] magic = new byte[2];
        int magicGot = 0;
        long got = 0;
        try {
            while (true) {
                int want = buf.length;
                if (expectedThisHop > 0) {
                    long left = expectedThisHop - got;
                    if (left <= 0) break;
                    if (left < want) want = (int) left;
                }
                int n = in.read(buf, 0, want);
                if (n < 0) break;
                if (n == 0) continue;
                if (already == 0 && magicGot < 2) {
                    int copy = Math.min(2 - magicGot, n);
                    System.arraycopy(buf, 0, magic, magicGot, copy);
                    magicGot += copy;
                    if (magicGot >= 2 && !ToolText.isApkMagic(magic, magicGot)) {
                        throw new IOException("not an apk (github sent html)");
                    }
                }
                out.write(buf, 0, n);
                got += n;
            }
            out.flush();
            out.getFD().sync();
            if (expectedThisHop > 0 && got < expectedThisHop) {
                throw new EOFException("truncated download (" + (already + got) + "/" + (already + expectedThisHop) + ")");
            }
            if (already == 0 && got == 0) throw new IOException("empty download");
            if (already == 0 && magicGot < 2 && got > 0) throw new IOException("not an apk");
        } finally {
            try { out.close(); } catch (Exception ignored) {}
        }
    }

    static void appendChunked(InputStream in, File tmp, boolean replace) throws IOException {
        FileOutputStream out = new FileOutputStream(tmp, !replace);
        try {
            while (true) {
                String line = readLine(in);
                if (line == null) throw new EOFException("truncated chunk");
                int semi = line.indexOf(';');
                String hex = (semi >= 0 ? line.substring(0, semi) : line).trim();
                int size;
                try { size = Integer.parseInt(hex, 16); }
                catch (Exception e) { throw new IOException("bad chunk size"); }
                if (size == 0) break;
                byte[] chunk = new byte[size];
                int got = 0;
                while (got < size) {
                    int n = in.read(chunk, got, size - got);
                    if (n < 0) throw new EOFException("truncated chunk");
                    got += n;
                }
                out.write(chunk);
                if (in.read() != '\r' || in.read() != '\n') throw new IOException("bad chunk trailer");
            }
            out.flush();
            out.getFD().sync();
        } finally {
            try { out.close(); } catch (Exception ignored) {}
        }
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int prev = 0, c;
        while ((c = in.read()) != -1) {
            if (prev == '\r' && c == '\n') break;
            if (prev == '\r') b.write('\r');
            prev = c;
            if (c != '\r') b.write(c);
            if (b.size() > 4096) throw new IOException("chunk line too long");
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.US_ASCII);
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

    static long totalSize(String contentRange, long contentLength, long have) {
        long fromRange = parseContentRangeTotal(contentRange);
        if (fromRange > 0) return fromRange;
        if (contentLength > 0) return have + contentLength;
        return -1;
    }

    static long parseContentRangeTotal(String range) {
        if (range == null) return -1;
        int slash = range.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= range.length()) return -1;
        try { return Long.parseLong(range.substring(slash + 1).trim()); }
        catch (Exception e) { return -1; }
    }

    static long parseContentRangeStart(String range) {
        if (range == null) return -1;
        String r = range.trim();
        int sp = r.indexOf(' ');
        int dash = r.indexOf('-');
        if (sp < 0 || dash < 0 || dash <= sp) return -1;
        try { return Long.parseLong(r.substring(sp + 1, dash).trim()); }
        catch (Exception e) { return -1; }
    }

    public static boolean isUsableApk(File file, long expected) {
        if (file == null || !file.exists() || file.length() < MIN_APK_BYTES) return false;
        if (!fileHasApkMagic(file)) return false;
        if (looksLikeCompleteZip(file)) return true;
        if (expected > 0 && file.length() == expected) return true;
        return false;
    }

    static boolean fileHasApkMagic(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] m = new byte[2];
            return in.read(m) == 2 && ToolText.isApkMagic(m, 2);
        } catch (Exception e) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    public static boolean looksLikeCompleteZip(File file) {
        if (file == null || file.length() < 22) return false;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long len = file.length();
            int max = (int) Math.min(len, 65557);
            byte[] tail = new byte[max];
            raf.seek(len - max);
            raf.readFully(tail);
            for (int i = tail.length - 22; i >= 0; i--) {
                if (tail[i] == 'P' && tail[i + 1] == 'K' && tail[i + 2] == 5 && tail[i + 3] == 6) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (raf != null) try { raf.close(); } catch (Exception ignored) {}
        }
    }

    static void moveTo(File tmp, File dest) throws IOException {
        if (dest.exists() && !dest.delete()) throw new IOException("couldn't replace apk");
        if (tmp.renameTo(dest)) return;
        copyFile(tmp, dest);
        tmp.delete();
    }

    public static void copyFile(File from, File to) throws IOException {
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

    static int httpCode(IOException e) {
        if (e == null || e.getMessage() == null) return 0;
        String m = e.getMessage();
        if (m.startsWith("http ")) {
            try { return Integer.parseInt(m.substring(5).trim().split("\\s+")[0]); }
            catch (Exception ignored) {}
        }
        return 0;
    }
}

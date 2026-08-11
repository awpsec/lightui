package com.lightos.minimalchat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure helpers for Obsidian note tool parsing (unit-testable on the JVM). */
public final class ObsidianTools {
    private ObsidianTools() {}

    public static final String MISSING_APP =
            "no Obsidian found on this device. install Obsidian to properly write notes.";

    /** Expandable chat row label — matches MainActivity glyphs (› collapsed, ˅ expanded). */
    public static String noteRowLabel(boolean expanded) {
        return "obsidian note" + (expanded ? " ˅" : " ›");
    }

    /**
     * Result of handling model tool markers for Obsidian notes.
     * {@code savedText} is shown under the expandable note row when present.
     */
    public static final class ToolOutcome {
        public final boolean showRow;
        public final String savedText;
        public final String cleanedAnswer;
        public final String writePath;
        public final String appendPath;
        public final String readPath;
        public final boolean notReady;

        public ToolOutcome(boolean showRow, String savedText, String cleanedAnswer,
                           String writePath, String appendPath, String readPath, boolean notReady) {
            this.showRow = showRow;
            this.savedText = savedText == null ? "" : savedText;
            this.cleanedAnswer = cleanedAnswer == null ? "" : cleanedAnswer;
            this.writePath = writePath == null ? "" : writePath;
            this.appendPath = appendPath == null ? "" : appendPath;
            this.readPath = readPath == null ? "" : readPath;
            this.notReady = notReady;
        }
    }

    /** Decide what the chat UI should show for Obsidian tools (before real vault I/O). */
    public static ToolOutcome planTool(String answer, boolean appInstalled, boolean enabled, boolean vaultLinked) {
        String text = answer == null ? "" : answer;
        String writeP = writePath(text);
        String appendP = appendPath(text);
        String readP = readPath(text);
        boolean hasTool = writeP.length() > 0 || appendP.length() > 0 || readP.length() > 0 || looksLikeTool(text);
        if (!hasTool) return new ToolOutcome(false, "", text, writeP, appendP, readP, false);
        boolean ready = appInstalled && enabled && vaultLinked;
        if (!ready) {
            String reason = notReadyReason(appInstalled, enabled, vaultLinked);
            return new ToolOutcome(true, reason, reason, writeP, appendP, readP, true);
        }
        return new ToolOutcome(true, "", text, writeP, appendP, readP, false);
    }

    /** Host-testable vault write/append against a real directory (mirrors SAF merge rules). */
    public static String writeVaultNote(File vaultRoot, String relativePath, String content, boolean append) {
        try {
            String path = normalizePath(relativePath);
            if (path.length() == 0) return "ERROR: missing note path";
            if (vaultRoot == null || !vaultRoot.isDirectory()) return "ERROR: no vault";
            File dest = new File(vaultRoot, path);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return "ERROR: couldn't create folders";
            String body = content == null ? "" : content;
            String mode;
            if (append) {
                if (!dest.exists()) {
                    mode = "created";
                    if (!body.endsWith("\n")) body = body + "\n";
                } else {
                    String prev = readVaultNote(vaultRoot, path);
                    if (prev.startsWith("ERROR:")) return prev;
                    body = mergeAppend(prev, body);
                    mode = "updated";
                }
            } else {
                mode = "wrote";
            }
            FileOutputStream out = new FileOutputStream(dest, false);
            try { out.write(body.getBytes(StandardCharsets.UTF_8)); out.flush(); }
            finally { try { out.close(); } catch (Exception ignored) { } }
            return mode + " " + path;
        } catch (Exception e) {
            return "ERROR: " + (e.getMessage() == null ? "write failed" : e.getMessage());
        }
    }

    public static String readVaultNote(File vaultRoot, String relativePath) {
        try {
            String path = normalizePath(relativePath);
            if (path.length() == 0) return "ERROR: missing note path";
            File dest = new File(vaultRoot, path);
            if (!dest.isFile()) return "ERROR: note not found: " + path;
            FileInputStream in = new FileInputStream(dest);
            try {
                byte[] buf = new byte[(int) Math.min(dest.length(), 2_000_000L)];
                int n = in.read(buf);
                if (n <= 0) return "";
                return new String(buf, 0, n, StandardCharsets.UTF_8);
            } finally { try { in.close(); } catch (Exception ignored) { } }
        } catch (Exception e) {
            return "ERROR: " + (e.getMessage() == null ? "read failed" : e.getMessage());
        }
    }

    public static String normalizePath(String path) {
        String p = path == null ? "" : path.trim().replace('\\', '/');
        p = p.replace("..", "");
        p = p.replaceAll("/+", "/");
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.length() == 0) return "";
        if (!p.toLowerCase(Locale.US).endsWith(".md")) p = p + ".md";
        return p;
    }

    public static String toolParam(String text, String function, String param) {
        if (text == null || function == null || param == null) return "";
        String lower = text.toLowerCase(Locale.US);
        String fn = function.toLowerCase(Locale.US);
        if (!lower.contains(fn)) return "";
        // Prefer the function body so mixed tool calls don't steal each other's params.
        String scope = text;
        Matcher block = Pattern.compile("(?is)<function\\s*=\\s*" + Pattern.quote(function) + "\\b[^>]*>(.*?)</function>").matcher(text);
        if (block.find()) scope = block.group(1);
        else {
            int at = lower.indexOf(fn);
            if (at >= 0) scope = text.substring(at);
        }
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("(?is)<parameter\\s*=\\s*" + param + "\\s*>(.*?)</parameter>"),
                Pattern.compile("(?is)<parameter\\s+name\\s*=\\s*[\"']?" + param + "[\"']?\\s*>(.*?)</parameter>"),
                Pattern.compile("(?is)[\"']?" + param + "[\"']?\\s*[:=]\\s*[\"']?(.*?)(?:[\"']?\\s*</parameter>|</function>|</tool_call>|\\n)")
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(scope);
            if (!m.find()) continue;
            String val;
            if ("content".equals(param)) {
                val = m.group(1) == null ? "" : m.group(1)
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&amp;", "&")
                        .trim();
            } else {
                val = (m.group(1) == null ? "" : m.group(1))
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                        .replace("\"", "")
                        .replace("'", "")
                        .trim();
            }
            if (val.length() > 0) return val;
        }
        return "";
    }

    public static String writePath(String text) {
        String p = toolParam(text, "obsidian_write", "path");
        if (p.length() == 0) p = toolParam(text, "obsidian_write", "note");
        if (p.length() == 0) p = toolParam(text, "obsidian_write", "title");
        return p;
    }

    public static String writeContent(String text) { return toolParam(text, "obsidian_write", "content"); }

    public static String appendPath(String text) {
        String p = toolParam(text, "obsidian_append", "path");
        if (p.length() == 0) p = toolParam(text, "obsidian_append", "note");
        if (p.length() == 0) p = toolParam(text, "obsidian_append", "title");
        return p;
    }

    public static String appendContent(String text) { return toolParam(text, "obsidian_append", "content"); }

    public static String readPath(String text) {
        String p = toolParam(text, "obsidian_read", "path");
        if (p.length() == 0) p = toolParam(text, "obsidian_read", "note");
        if (p.length() == 0) p = toolParam(text, "obsidian_read", "title");
        return p;
    }

    public static boolean looksLikeTool(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.US);
        return lower.contains("obsidian_write") || lower.contains("obsidian_append") || lower.contains("obsidian_read");
    }

    public static String mergeAppend(String existing, String addition) {
        String prev = existing == null ? "" : existing;
        String body = addition == null ? "" : addition;
        StringBuilder merged = new StringBuilder(prev);
        if (merged.length() > 0 && merged.charAt(merged.length() - 1) != '\n') merged.append('\n');
        merged.append(body);
        if (merged.length() == 0 || merged.charAt(merged.length() - 1) != '\n') merged.append('\n');
        return merged.toString();
    }

    public static String notReadyReason(boolean appInstalled, boolean enabled, boolean vaultLinked) {
        if (!appInstalled) return MISSING_APP;
        if (!vaultLinked) return "link your Obsidian vault in settings before writing notes.";
        if (!enabled) return "turn on Obsidian notes in settings first.";
        return "Obsidian notes aren't ready yet.";
    }
}

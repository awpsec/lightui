package com.lightos.minimalchat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure helpers for Obsidian note tool parsing (unit-testable on the JVM). */
public final class ObsidianTools {
    private ObsidianTools() {}

    public static final String MISSING_APP =
            "no Obsidian found on this device. install Obsidian to properly write notes.";

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

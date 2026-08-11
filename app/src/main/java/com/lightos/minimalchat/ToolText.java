package com.lightos.minimalchat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure helpers for stripping tool markup and judging search follow-up needs (JVM-testable). */
public final class ToolText {
    private ToolText() {}

    private static final Pattern GOOGLE_QUERY = Pattern.compile(
            "(?is)\\[?\\s*(?:google|web[_\\s-]?search|search)\\s*\\(\\s*query\\s*=\\s*[\"']?([^\"'\\)\\]\\n{}]+)[\"']?\\s*\\)\\s*\\]?");
    private static final Pattern PARAM_QUERY = Pattern.compile(
            "(?is)<parameter(?:\\s+name\\s*=\\s*[\"']?query[\"']?|\\s*=\\s*query)[^>]*>(.*?)</parameter>");
    private static final Pattern JSON_QUERY = Pattern.compile(
            "(?is)[\"']query[\"']\\s*:\\s*[\"']([^\"']+)[\"']");
    private static final Pattern INVOKE_QUERY = Pattern.compile(
            "(?is)(?:invoke|call|run)\\s+(?:tool\\s+)?(?:web[_\\s-]?search|google)[^\\n]{0,80}?(?:query|q)\\s*[:=]\\s*[\"']?([^\"'\\n<]+)[\"']?");

    public static String stripToolCalls(String text) {
        if (text == null || text.length() == 0) return "";
        String s = text;
        // Fenced tool dumps models sometimes emit.
        s = s.replaceAll("(?is)```(?:xml|json|tool|function)?\\s*(?:<tool_call|<\\|tool_call|<function=|\\{\\s*\"name\"\\s*:\\s*\"web_search)[\\s\\S]*?```", "");
        s = s.replaceAll("(?is)<tool_call\\b[^>]*>.*?</tool_call>", "");
        s = s.replaceAll("(?is)<function\\s*=\\s*(?:save_memory|remove_memory|web_search|obsidian_write|obsidian_append|obsidian_read)\\b[^>]*>.*?</function>", "");
        // Incomplete function open without '>' (common bleed)
        s = s.replaceAll("(?is)<function\\s*=\\s*(?:save_memory|remove_memory|web_search|obsidian_write|obsidian_append|obsidian_read)\\b[^>\\n]*$", "");
        s = s.replaceAll("(?is)<\\|/?tool[_\\s-]?calls?(?:_section)?(?:_started|_ended|_begin|_end)?\\|>.*?<\\|/?tool[_\\s-]?calls?(?:_section)?(?:_started|_ended|_begin|_end)?\\|>", "");
        s = s.replaceAll("(?is)<\\|tool_call_(?:begin|start|started)\\|>[\\s\\S]*?(?:<\\|tool_call_(?:end|ended)\\|>|$)", "");
        s = s.replaceAll("(?is)\\[(?:google|web[_\\s-]?search|search|bing|brave)\\s*\\([^\\]]*\\)\\]", "");
        s = s.replaceAll("(?is)\\{\\s*\"name\"\\s*:\\s*\"(?:web_search|google|save_memory|remove_memory)\"[\\s\\S]*?\\}(?:\\s*$)?", "");
        s = s.replaceAll("(?is)<\\|/?tool[_\\s-]?calls?(?:_section)?(?:_started|_ended|_begin|_end)?\\|>[\\s\\S]*$", "");
        s = s.replaceAll("(?is)<tool_call\\b[^>]*>?[\\s\\S]*$", "");
        s = s.replaceAll("(?is)<function\\s*=\\s*(?:save_memory|remove_memory|web_search|obsidian_write|obsidian_append|obsidian_read)\\b[^>]*>?[\\s\\S]*$", "");
        s = s.replaceAll("(?is)\\[(?:google|web[_\\s-]?search|search)\\s*\\([^\\]]*$", "");
        s = s.replaceAll("(?is)(?:^|\\n)\\s*(?:invoke|call|run)\\s+(?:tool\\s+)?(?:web[_\\s-]?search|google|save_memory|remove_memory)[^\\n]*", "\n");
        return s.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    public static boolean looksLikeToolResidue(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.US).trim();
        if (lower.length() == 0) return false;
        if (lower.contains("<tool_call") || lower.contains("<|tool_call") || lower.contains("tool_call_started")
                || lower.contains("tool_call_ended") || lower.contains("tool_call_begin") || lower.contains("tool_call_end")
                || lower.contains("[google(") || lower.contains("[web_search") || lower.contains("[search(")
                || lower.contains("<function=web_search") || lower.contains("<function=save_memory")
                || lower.contains("<function=remove_memory") || lower.contains("<function=obsidian_")
                || lower.contains("\"name\":\"web_search\"") || lower.contains("\"name\": \"web_search\"")) {
            return true;
        }
        if (lower.contains("web_search") && (lower.contains("<") || lower.contains("{") || lower.contains("invoke") || lower.contains("parameter"))) {
            return true;
        }
        if (lower.contains("obsidian_") && lower.contains("<")) return true;
        // Nearly-only markup / fence leftovers after a failed strip.
        String stripped = stripToolCalls(text);
        if (stripped.length() == 0 && lower.length() > 0) return true;
        if (stripped.length() > 0 && stripped.length() < 24 && (lower.contains("function=") || lower.contains("tool_call"))) return true;
        return false;
    }

    /**
     * Meta / planning replies that should NOT be shown when search sources already exist —
     * trigger a real follow-up answer instead.
     */
    public static boolean looksLikeSearchPlanning(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() == 0) return false;
        if (looksLikeToolResidue(t)) return true;
        String lower = t.toLowerCase(Locale.US).replace('’', '\'');
        // Short planning / scaffolding lines models emit after seeing sources.
        String[] needles = new String[]{
                "i need to answer",
                "i should answer",
                "i will answer",
                "i'll answer",
                "now i need to",
                "now i should",
                "let me answer",
                "let me use the",
                "using the sources",
                "based on the sources i",
                "i'll use the search",
                "i will use the search",
                "i need to use the search",
                "i should use the search",
                "search results provided",
                "the search has already",
                "web search has already",
                "do not emit tool",
                "i'll look that up",
                "i will look that up",
                "let me search",
                "i'll search",
                "i will search",
                "searching for",
                "i need to search",
                "i should search",
                "going to search",
                "call web_search",
                "calling web_search",
                "use web_search",
                "perform a search",
                "perform web search"
        };
        for (String n : needles) {
            if (lower.contains(n)) {
                // If the message is short planning, or planning dominates, treat as non-answer.
                if (t.length() < 280) return true;
                // Longer text that starts with planning still needs follow-up.
                String head = lower.length() > 120 ? lower.substring(0, 120) : lower;
                if (head.contains(n)) return true;
            }
        }
        return false;
    }

    public static boolean needsSearchFollowup(String rawAnswer, String sanitizedAnswer, boolean hasSearchContext) {
        if (webSearchToolQuery(rawAnswer).length() > 0) return true;
        if (!hasSearchContext) return false;
        String clean = sanitizedAnswer == null ? "" : sanitizedAnswer.trim();
        if (clean.length() == 0) return true;
        if (looksLikeToolResidue(rawAnswer) || looksLikeToolResidue(clean)) return true;
        if (looksLikeSearchPlanning(clean) || looksLikeSearchPlanning(rawAnswer)) return true;
        return false;
    }

    public static String webSearchToolQuery(String text) {
        String s = text == null ? "" : text.trim();
        String lower = s.toLowerCase(Locale.US);
        if (!lower.contains("web_search") && !lower.contains("tool_call") && !lower.contains("[google(")
                && !lower.contains("google(query") && !lower.contains("[search(") && !lower.contains("\"query\"")
                && !lower.contains("invoke") && !lower.contains("call web")) {
            return "";
        }
        Matcher google = GOOGLE_QUERY.matcher(s);
        if (google.find()) {
            String q = cleanQuery(google.group(1));
            if (q.length() > 0) return q;
        }
        Matcher param = PARAM_QUERY.matcher(s);
        if (param.find()) {
            String q = cleanQuery(param.group(1));
            if (q.length() > 0) return q;
        }
        Matcher json = JSON_QUERY.matcher(s);
        if (json.find()) {
            String q = cleanQuery(json.group(1));
            if (q.length() > 0) return q;
        }
        Matcher invoke = INVOKE_QUERY.matcher(s);
        if (invoke.find()) {
            String q = cleanQuery(invoke.group(1));
            if (q.length() > 0) return q;
        }
        String[] markers = new String[]{"<parameter=query>", "query:", "query=", "\"query\":", "'query':"};
        for (String marker : markers) {
            int at = lower.indexOf(marker);
            if (at < 0) continue;
            int start = at + marker.length();
            while (start < s.length() && Character.isWhitespace(s.charAt(start))) start++;
            int end = s.length();
            String[] stops = new String[]{"</parameter>", "</function>", "</tool_call>", "</|tool_call|>",
                    "<|tool_call_ended|>", "<|tool_call_end|>", "\n", ")", "]", "}", ","};
            for (String stop : stops) {
                int cut = lower.indexOf(stop, start);
                if (cut >= 0) end = Math.min(end, cut);
            }
            String q = cleanQuery(s.substring(start, end));
            if (q.length() > 0) return q;
        }
        return "";
    }

    private static String cleanQuery(String q) {
        if (q == null) return "";
        String out = q.replace("\"", "").replace("'", "").replace("&quot;", "").trim();
        out = out.replaceAll("[{}\\]]+$", "").trim();
        return out;
    }

    public static String cleanAfterToolStrip(String text) {
        String s = text == null ? "" : text;
        s = s.replaceAll("(?is)(?:i(?:'|’)ll|i will|i(?:'|’)m going to|i am going to|i(?:'|’)ve|i have)\\s+(?:save|saved|remove|removed|delete|deleted|forget|forgot)[^.!?\\n]{0,100}[:,-]?\\s*$", "");
        s = s.replaceAll("(?is)(?:i(?:'|’)ll|i will|let me|i(?:'|’)m going to)\\s+(?:search|look up|look that up|use web_search|call web_search)[^.!?\\n]{0,120}[:,-]?\\s*$", "");
        s = s.replaceAll("[\\s:,-]+$", "").trim();
        return s;
    }

    public static String cleanSearchArtifacts(String s) {
        return (s == null ? "" : s)
                .replaceAll("\\[[0-9]+%L[0-9]+(?:-L[0-9]+)?\\]", "")
                .replaceAll("\\[[0-9]+[†‡]L[0-9]+(?:-L[0-9]+)?\\]", "")
                .replaceAll("【[^】]*[†‡%]L[0-9][^】]*】", "")
                .replaceAll("(?<=\\p{Alpha})[†‡](?=\\p{Alpha})", " ")
                .replace("†", "")
                .replace("‡", "");
    }

    public static String sanitizeAssistantText(String text) {
        return cleanSearchArtifacts(cleanAfterToolStrip(stripToolCalls(text))).trim();
    }

    /** Cut streamed answer before tool markup starts (avoid showing raw calls mid-stream). */
    public static String visibleStreamingAnswer(String text) {
        String s = text == null ? "" : text;
        String cleaned = stripToolCalls(s);
        String lower = cleaned.toLowerCase(Locale.US);
        int cut = -1;
        // Prefer structural markers — avoid cutting prose that merely mentions "web_search".
        String[] markers = new String[]{
                "<|tool_call", "<|tool_calls", "tool_call_started", "tool_call_ended", "tool_call_begin",
                "<tool_call", "<function", "[google(", "[web_search", "[search(",
                "\"name\":\"web_search\"", "\"name\": \"web_search\"",
                "<function=save_memory", "<function=remove_memory",
                "<function=obsidian_write", "<function=obsidian_append", "<function=obsidian_read"
        };
        for (String marker : markers) {
            int at = lower.indexOf(marker);
            if (at >= 0) cut = cut < 0 ? at : Math.min(cut, at);
        }
        if (cut < 0) {
            String[] prefixes = new String[]{"<|tool_call", "<|tool_calls", "<tool_call", "<function", "[google", "[web_search", "[search"};
            int start = Math.max(0, lower.length() - 40);
            for (int i = start; i < lower.length(); i++) {
                String tail = lower.substring(i);
                if (tail.length() == 0) continue;
                for (String prefix : prefixes) {
                    if (prefix.startsWith(tail)) { cut = i; break; }
                }
                if (cut >= 0) break;
            }
        }
        if (cut >= 0) cleaned = cleaned.substring(0, cut);
        return cleanAfterToolStrip(cleaned);
    }

    public static boolean isUsableFollowupAnswer(String text) {
        String v = sanitizeAssistantText(text);
        if (v.length() == 0) return false;
        if (looksLikeToolResidue(v)) return false;
        if (looksLikeSearchPlanning(v)) return false;
        return true;
    }

    public static String webSearchToolsPrompt() {
        return "Web search tool: when you need fresh facts (scores, news, prices, schedules, weather, \"look up\" / \"search for\"), "
                + "answer nothing yet — append ONE tool call at the very end: "
                + "<tool_call><function=web_search><parameter=query>short search query</parameter></function></tool_call> "
                + "Never reply with only a tool call wrapped in markdown fences. Never invent other tool XML formats. "
                + "If search results were already provided in the system context, answer directly and do not emit tool calls.";
    }
}

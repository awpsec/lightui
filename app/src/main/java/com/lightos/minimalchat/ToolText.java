package com.lightos.minimalchat;

import java.util.ArrayList;
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
    private static final Pattern JSON_Q = Pattern.compile(
            "(?is)[\"']q[\"']\\s*:\\s*[\"']([^\"']+)[\"']");
    private static final Pattern POSITIONAL_QUERY = Pattern.compile(
            "(?is)\\b(?:web[_\\s-]?search|google|search)\\s*\\(\\s*[\"']([^\"']{2,200})[\"']\\s*\\)");
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
        // Simple SEARCH: fallback dialect (line at end or alone).
        s = s.replaceAll("(?im)^\\s*SEARCH:\\s*.*$", "");
        s = s.replaceAll("(?is)\\n\\s*SEARCH:\\s*[\\s\\S]*$", "");
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
                || lower.contains("\"name\":\"web_search\"") || lower.contains("\"name\": \"web_search\"")
                || lower.matches("(?s).*(?:^|\\n)\\s*search:\\s*\\S.*")) {
            return true;
        }
        if (lower.contains("web_search") && (lower.contains("<") || lower.contains("{") || lower.contains("invoke") || lower.contains("parameter"))) {
            return true;
        }
        if (lower.contains("obsidian_") && lower.contains("<")) return true;
        // Nearly-only markup / fence leftovers after a failed strip.
        String stripped = stripToolCalls(text);
        if (stripped.length() == 0 && lower.length() > 0) return true;
        if (stripped.length() > 0 && stripped.length() < 24 && (lower.contains("function=") || lower.contains("tool_call") || lower.contains("search:"))) return true;
        return false;
    }

    /** True when the text has digits/prices/dates — almost never pure planning. */
    public static boolean containsConcreteFact(String text) {
        if (text == null || text.trim().length() == 0) return false;
        String t = text;
        String lower = t.toLowerCase(Locale.US);
        if (t.matches("(?s).*\\b(\\$|€|£|¥)\\s?\\d.*")) return true;
        if (t.matches("(?s).*\\b\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?\\b.*")) return true;
        if (t.matches("(?s).*\\b\\d+(?:\\.\\d+)?\\s*%.*")) return true;
        if (t.matches("(?s).*\\b\\d{2,4}\\s*(gb|tb|mhz|ghz|cl\\d+|mm|kg|lb)\\b.*")) return true;
        // Years and month+day — do NOT treat modal verb "may" as a date.
        if (t.matches("(?s).*\\b20\\d{2}\\b.*")) return true;
        if (lower.matches("(?s).*\\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|jun(?:e)?|"
                + "jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
                + "\\.?\\s+\\d{1,4}\\b.*")) {
            return true;
        }
        if (lower.matches("(?s).*\\bmay\\s+\\d{1,2}\\b.*") || lower.matches("(?s).*\\b\\d{1,2}\\s+may\\b.*")) {
            return true;
        }
        // At least one standalone number of 2+ digits (prices, scores, counts).
        return t.matches("(?s).*\\b\\d{2,}\\b.*");
    }

    /**
     * Meta / planning replies that should NOT be shown when search sources already exist —
     * trigger a real follow-up answer instead.
     */
    public static boolean looksLikeSearchPlanning(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() == 0) return false;
        if (looksLikeToolResidue(t)) return true;
        // Real answers with prices/numbers/dates are never "planning".
        if (containsConcreteFact(t)) return false;
        String lower = t.toLowerCase(Locale.US).replace('’', '\'');
        // Short planning / scaffolding lines models emit after seeing sources.
        String[] hardNeedles = new String[]{
                "i need to answer",
                "i should answer",
                "i will answer",
                "i'll answer",
                "now i need to",
                "now i should",
                "let me answer",
                "let me use the",
                "using the sources",
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
                "looking that up",
                "looking up",
                "let me search",
                "i'll search",
                "i will search",
                "i need to search",
                "i should search",
                "going to search",
                "call web_search",
                "calling web_search",
                "use web_search",
                "perform a search",
                "perform web search"
        };
        for (String n : hardNeedles) {
            if (lower.contains(n)) {
                // If the message is short planning, or planning dominates, treat as non-answer.
                if (t.length() < 280) return true;
                // Longer text that starts with planning still needs follow-up.
                String head = lower.length() > 120 ? lower.substring(0, 120) : lower;
                if (head.contains(n)) return true;
            }
        }
        // Soft phrases that often appear in real answers — only reject when they dominate a short reply.
        String[] softNeedles = new String[]{
                "searching for",
                "based on the sources i",
                "according to the sources i"
        };
        for (String n : softNeedles) {
            if (!lower.contains(n)) continue;
            if (t.length() <= 80) return true;
            if (lower.indexOf(n) < 12 && t.length() < 140) return true;
        }
        return false;
    }

    /**
     * Whether a completion still needs a search+synth pass.
     * A usable sanitized answer wins over residual tool markup / trailing SEARCH: lines —
     * never wipe a finished reply just because the model also emitted a tool call.
     */
    public static boolean needsSearchFollowup(String rawAnswer, String sanitizedAnswer, boolean hasSearchContext) {
        String clean = sanitizedAnswer == null ? "" : sanitizedAnswer.trim();
        boolean usable = isUsableFollowupAnswer(clean);
        String toolQ = webSearchToolQuery(rawAnswer);
        if (usable) {
            // Brief ack + tool ("Sure.\nSEARCH: …") still needs the real search path.
            if (toolQ.length() > 0 && clean.length() < 40 && !containsConcreteFact(clean)) return true;
            return false;
        }
        if (toolQ.length() > 0) return true;
        if (!hasSearchContext) return false;
        if (clean.length() == 0) return true;
        if (looksLikeToolResidue(rawAnswer) || looksLikeToolResidue(clean)) return true;
        if (looksLikeSearchPlanning(clean) || looksLikeSearchPlanning(rawAnswer)) return true;
        return false;
    }

    public static String webSearchToolQuery(String text) {
        String s = text == null ? "" : text.trim();
        String lower = s.toLowerCase(Locale.US);
        // Simple SEARCH: dialect for weak/local models (prefer end-of-message line).
        Matcher simple = Pattern.compile("(?im)^\\s*SEARCH:\\s*(.+?)\\s*$").matcher(s);
        String simpleQ = "";
        while (simple.find()) simpleQ = cleanQuery(simple.group(1));
        if (simpleQ.length() > 0) return simpleQ;
        if (!lower.contains("web_search") && !lower.contains("tool_call") && !lower.contains("[google(")
                && !lower.contains("google(query") && !lower.contains("[search(") && !lower.contains("\"query\"")
                && !lower.contains("\"q\"") && !lower.contains("'q'")
                && !lower.contains("web_search(") && !lower.contains("google(") && !lower.contains("search(")
                && !lower.contains("invoke") && !lower.contains("call web") && !lower.contains("search:")) {
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
        Matcher jsonQ = JSON_Q.matcher(s);
        if (jsonQ.find()) {
            String q = cleanQuery(jsonQ.group(1));
            if (q.length() > 0) return q;
        }
        Matcher positional = POSITIONAL_QUERY.matcher(s);
        if (positional.find()) {
            String q = cleanQuery(positional.group(1));
            if (q.length() > 0) return q;
        }
        Matcher invoke = INVOKE_QUERY.matcher(s);
        if (invoke.find()) {
            String q = cleanQuery(invoke.group(1));
            if (q.length() > 0) return q;
        }
        String[] markers = new String[]{"<parameter=query>", "query:", "query=", "\"query\":", "'query':", "\"q\":", "'q':"};
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
                "<function=obsidian_write", "<function=obsidian_append", "<function=obsidian_read",
                "\nsearch:", "\nsearch :"
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
        if (looksLikeSourceMetadataOnly(v)) return false;
        if (looksLikeInternalMonologue(v)) return false;
        // Pure punctuation / braces left after stripping JSON tool wrappers ("}").
        if (v.matches("^[\\s\\p{Punct}]+$")) return false;
        int alnum = 0;
        for (int i = 0; i < v.length(); i++) {
            if (Character.isLetterOrDigit(v.charAt(i))) alnum++;
            if (alnum >= 2) break;
        }
        return alnum >= 2;
    }

    /**
     * True when the visible reply is blank or only private CoT / tool residue —
     * the main stream path must recover instead of finishing with an empty body.
     */
    public static boolean needsEmptyReplyRecovery(String rawAnswer, String sanitizedAnswer) {
        String clean = sanitizedAnswer == null ? "" : sanitizeAssistantText(sanitizedAnswer);
        if ("searching...".equals(clean.trim())) return true;
        if (clean.length() == 0) return true;
        if (!isUsableFollowupAnswer(clean)) return true;
        // A usable post-search / sanitized answer always wins — never wipe it just because
        // the raw stream was a tool call (that was the "No reply" overwrite bug).
        return false;
    }

    /** Private chain-of-thought that models sometimes emit as the only "content". */
    public static boolean looksLikeInternalMonologue(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() == 0) return false;
        if (containsConcreteFact(t)) return false;
        String lower = t.toLowerCase(Locale.US).replace('’', '\'');
        String[] needles = new String[]{
                "the user said",
                "the user is testing",
                "the user wants",
                "the user asked",
                "the user sent",
                "user just said",
                "i should respond",
                "i need to respond",
                "i will respond",
                "i'll respond",
                "let me respond",
                "my response should",
                "i should reply",
                "i need to reply",
                "i'll reply",
                "keep my reply",
                "respond politely",
                "acknowledge that",
                "this is a test message",
                "simple test message",
                "they are testing"
        };
        for (String n : needles) {
            if (!lower.contains(n)) continue;
            if (t.length() < 320) return true;
            String head = lower.length() > 140 ? lower.substring(0, 140) : lower;
            if (head.contains(n)) return true;
        }
        return false;
    }

    public static String emptyReplyFollowupSystem() {
        return "Your previous reply was empty or only private thinking. "
                + "Answer the user's message now in plain text. "
                + "Do not output tool calls, XML, function calls, or chain-of-thought. "
                + "Keep it brief and natural.\n\n";
    }

    /** Title/citation dumps that are not real answers (e.g. "[1] Title: …"). */
    public static boolean looksLikeSourceMetadataOnly(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() == 0) return false;
        String lower = t.toLowerCase(Locale.US);
        if (lower.startsWith("from the gathered sources:")) {
            String rest = t.substring("from the gathered sources:".length()).trim();
            return rest.length() == 0 || isSourceMetaLine(rest);
        }
        if (isSourceMetaLine(t)) return true;
        // Single-line answers that are only a headline / citation header.
        if (!t.contains("\n") && t.length() < 160 && isSourceMetaLine(t)) return true;
        return false;
    }

    public static boolean isSourceMetaLine(String line) {
        String t = line == null ? "" : line.trim();
        if (t.length() == 0) return true;
        String lower = t.toLowerCase(Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return true;
        if (lower.startsWith("url source:") || lower.startsWith("url:") || lower.startsWith("link:")) return true;
        if (lower.startsWith("published date:") || lower.startsWith("published:") || lower.startsWith("date:")) return true;
        if (lower.startsWith("title:") || lower.matches("(?i)^\\[?\\d+\\]?\\.?\\s*title\\s*:.*")) return true;
        if (lower.matches("(?i)^\\[\\d+\\]\\s*title\\s*:.*")) return true;
        if (lower.matches("(?i)^\\[\\d+\\]\\s*.{0,120}") && (lower.contains("title:") || lower.length() < 90)) return true;
        // Numbered dump / headline lines — not normal answers like "1. The Lakers won 112-108".
        if (t.matches("(?i)^\\d+[.)]\\s*.{0,140}$")
                && (t.contains(" - ") || t.contains(" | ") || lower.contains("http")
                || lower.contains("title:") || lower.contains("snippet:") || lower.contains("url:"))) {
            return true;
        }
        if (t.matches("(?i)^\\d+[.)]\\s+\\S.{0,100}$")
                && !containsConcreteFact(t)
                && !lower.matches("(?i).*\\b(won|beat|lost|is|are|was|were|costs?|priced?|around|about|typically|between)\\b.*")
                && t.length() < 90) {
            // Brave-style "1. Some Article Headline" without sentence/fact cues.
            return true;
        }
        if (lower.equals("description:") || lower.startsWith("description:") && t.length() < 24) return true;
        return false;
    }

    /**
     * Last-resort readable snippet from Jina/Brave plain-text results.
     * Prefers description/body lines; never returns bare titles alone.
     */
    public static String searchSnippetFallback(String result) {
        if (result == null || result.trim().length() == 0) return "";
        String[] lines = result.replace('\r', '\n').split("\n");
        String bestDesc = "";
        String bestBody = "";
        StringBuilder juicyBits = new StringBuilder();
        String pendingTitle = "";
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.length() == 0) continue;
            String lower = t.toLowerCase(Locale.US);
            if (lower.startsWith("description:")) {
                String d = t.substring("description:".length()).trim();
                if (d.length() >= 24 && !isSourceMetaLine(d)) {
                    if (bestDesc.length() == 0 || d.length() > bestDesc.length()) bestDesc = d;
                    if (juicyBits.length() < 500 && (d.matches("(?i).*\\b(\\$|usd|€|£|gb|price|cost|\\d{2,}).*") || juicyBits.length() == 0)) {
                        if (juicyBits.length() > 0) juicyBits.append(' ');
                        juicyBits.append(d);
                    }
                }
                continue;
            }
            if (lower.startsWith("title:") || lower.matches("(?i)^\\[\\d+\\]\\s*title\\s*:.*") || lower.matches("(?i)^\\d+\\.\\s+\\S.{0,100}")) {
                pendingTitle = t.replaceFirst("(?i)^(?:\\[\\d+\\]\\s*)?(?:title\\s*:\\s*)?", "").trim();
                continue;
            }
            if (isSourceMetaLine(t)) continue;
            if (t.length() < 24) continue;
            boolean juicy = lower.matches(".*\\b(\\$|usd|€|£|gb|tb|mhz|cl\\d+|price|cost|\\d{2,}).*");
            if (juicy) {
                if (bestBody.length() == 0 || t.length() > bestBody.length()) bestBody = t;
                if (juicyBits.length() < 500) {
                    if (juicyBits.length() > 0) juicyBits.append(' ');
                    juicyBits.append(t);
                }
            } else if (bestBody.length() == 0) {
                bestBody = t;
            }
        }
        if (juicyBits.length() >= 24) {
            String pick = juicyBits.toString().trim();
            if (!looksLikeSourceMetadataOnly(pick)) return pick.length() > 700 ? pick.substring(0, 700).trim() + "…" : pick;
        }
        String pick = bestDesc.length() > 0 ? bestDesc : bestBody;
        if (pick.length() == 0 && pendingTitle.length() > 0) {
            // Absolute last resort: title alone is better than "No reply".
            return pendingTitle;
        }
        if (pick.length() == 0) return "";
        if (looksLikeSourceMetadataOnly(pick) && bestDesc.length() == 0) return "";
        return pick;
    }

    public static String webSearchToolsPrompt() {
        return "When you need fresh facts (scores, news, prices, schedules, weather, look-ups), "
                + "do not answer yet. End your message with EXACTLY ONE of these tool forms:\n"
                + "1) Preferred (simple): SEARCH: short search query\n"
                + "2) XML: <tool_call><function=web_search><parameter=query>short search query</parameter></function></tool_call>\n"
                + "Put the tool call at the very end. No markdown fences around it. "
                + "If search results were already provided, answer directly — do not emit another tool call.";
    }

    public static String webSearchFollowupSystem(boolean retry) {
        return webSearchFollowupSystem(retry, retry ? 1 : 0);
    }

    public static String webSearchFollowupSystem(boolean retry, int attemptIndex) {
        String base;
        if (attemptIndex >= 2) {
            base = "Final research pass. Prior replies were unusable. "
                    + "Answer NOW with the best concrete numbers/facts from the sources in the user message. ";
        } else if (retry || attemptIndex >= 1) {
            base = "Your previous reply was not a usable answer (empty, tool call, planning, or only a source title). "
                    + "The web search already ran. Answer the user's question NOW in plain text using the sources in the user message. ";
        } else {
            base = "Web search results are in the user message. Answer the question directly in plain text. ";
        }
        return base
                + "Do not output tool calls, XML, function calls, SEARCH: lines, or google(...) markers. "
                + "Give concrete facts from the snippets (prices, numbers, dates, ranges). "
                + "If prices vary by seller, give a typical current range and mention it varies. "
                + "Never reply with only a source title, '[1] Title: …', or a bare headline. "
                + "Cite a URL only when helpful.\n\n";
    }

    public static final class SlashCommand {
        public final String name;
        public final String description;
        public SlashCommand(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public static final class SlashParse {
        public final String name;
        public final String args;
        public SlashParse(String name, String args) {
            this.name = name == null ? "" : name;
            this.args = args == null ? "" : args;
        }
    }

    public static final SlashCommand[] SLASH_COMMANDS = new SlashCommand[]{
            new SlashCommand("search", "search the web"),
            new SlashCommand("research", "deep search — up to 3 synthesis passes"),
            new SlashCommand("help", "list slash commands"),
            new SlashCommand("memory", "show persistent memory"),
            new SlashCommand("new", "start a new chat"),
            new SlashCommand("web", "toggle web search for this chat"),
    };

    public static SlashParse parseSlash(String text) {
        String t = text == null ? "" : text.trim();
        if (!t.startsWith("/")) return null;
        String body = t.substring(1).trim();
        if (body.length() == 0) return null;
        int sp = body.indexOf(' ');
        String name = (sp < 0 ? body : body.substring(0, sp)).toLowerCase(Locale.US);
        String args = sp < 0 ? "" : body.substring(sp + 1).trim();
        if (name.length() == 0) return null;
        for (SlashCommand c : SLASH_COMMANDS) {
            if (c.name.equals(name)) return new SlashParse(name, args);
        }
        return null;
    }

    /** Filter commands while the user types `/se` or `/search ` (before args get long). */
    public static ArrayList<SlashCommand> filterSlashCommands(String raw) {
        ArrayList<SlashCommand> out = new ArrayList<SlashCommand>();
        String t = raw == null ? "" : raw;
        if (!t.startsWith("/")) return out;
        // Hide once they are typing the query after a known command + space.
        if (t.matches("(?is)^/(search|research)\\s+\\S.*")) return out;
        String token = t.substring(1);
        int sp = token.indexOf(' ');
        String partial = (sp < 0 ? token : token.substring(0, sp)).toLowerCase(Locale.US);
        for (SlashCommand c : SLASH_COMMANDS) {
            if (partial.length() == 0 || c.name.startsWith(partial)) out.add(c);
        }
        return out;
    }
}

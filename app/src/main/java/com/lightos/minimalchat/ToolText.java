package com.lightos.minimalchat;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

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
        s = s.replaceAll("(?im)^\\s*FETCH:\\s*.*$", "");
        s = s.replaceAll("(?is)\\n\\s*FETCH:\\s*[\\s\\S]*$", "");
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
                || lower.matches("(?s).*(?:^|\\n)\\s*search:\\s*\\S.*")
                || lower.matches("(?s).*(?:^|\\n)\\s*fetch:\\s*https?://\\S.*")) {
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

    public static String fetchToolUrl(String text) {
        String s = text == null ? "" : text.trim();
        if (s.length() == 0) return "";
        Matcher simple = Pattern.compile("(?im)^\\s*FETCH:\\s*(\\S+)\\s*$").matcher(s);
        String url = "";
        while (simple.find()) url = simple.group(1).trim();
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        Matcher json = Pattern.compile("(?is)[\"']url[\"']\\s*:\\s*[\"'](https?://[^\"']+)[\"']").matcher(s);
        if (json.find()) return json.group(1).trim();
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
        if (looksLikeSearchPunt(v)) return false;
        // Pure punctuation / braces left after stripping JSON tool wrappers ("}").
        if (v.matches("^[\\s\\p{Punct}]+$")) return false;
        int alnum = 0;
        for (int i = 0; i < v.length(); i++) {
            if (Character.isLetterOrDigit(v.charAt(i))) alnum++;
            if (alnum >= 2) break;
        }
        return alnum >= 2;
    }

    public static boolean containsPriceAmount(String text) {
        if (text == null || text.length() == 0) return false;
        if (text.matches("(?s).*(\\$|€|£)\\s?\\d.*")) return true;
        String lower = text.toLowerCase(Locale.US);
        return lower.matches("(?s).*\\b\\d+(?:[.,]\\d+)?\\s*(usd|dollars?)\\b.*");
    }

    /**
     * "I don't have the price, look it up yourself" — not an answer when we just searched.
     * Real answers that include a $ / € / £ amount are never punts.
     */
    public static boolean looksLikeSearchPunt(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() == 0) return false;
        if (containsPriceAmount(t)) return false;
        String lower = t.toLowerCase(Locale.US).replace('’', '\'').replace('`', '\'');
        lower = lower.replace("'", "");
        String[] needles = new String[]{
                "dont have specific",
                "do not have specific",
                "no specific pricing",
                "no specific current",
                "no current pricing",
                "doesnt provide specific",
                "does not provide specific",
                "doesnt include specific",
                "does not include specific",
                "look it up",
                "look that up yourself",
                "search for it yourself",
                "youll need to check",
                "you will need to check",
                "i recommend checking",
                "i recommend visiting",
                "please check amazon",
                "please check newegg",
                "check a retailer",
                "check retailers",
                "i cannot find specific",
                "couldnt find specific",
                "could not find specific",
                "unable to find specific",
                "not enough information in the",
                "the sources dont",
                "the available context only",
                "available context only includes",
                "i dont have access to live",
                "i dont have access to current",
                "i dont have access to real-time"
        };
        for (int i = 0; i < needles.length; i++) {
            if (lower.contains(needles[i])) return true;
        }
        return false;
    }

    public static boolean looksLikePriceQuery(String query) {
        String l = query == null ? "" : query.toLowerCase(Locale.US);
        if (l.length() == 0) return false;
        if (l.contains("price") || l.contains("cost") || l.contains("how much") || l.contains("going for")
                || l.contains("usd") || l.contains("$")) return true;
        boolean ram = l.contains("ddr") || l.contains("ram");
        return ram && (l.contains("gb") || l.contains("kit") || l.contains("mhz") || l.contains("cl"));
    }

    public static boolean searchResultsLackFacts(String result) {
        String t = result == null ? "" : result.trim();
        if (t.length() == 0) return true;
        String lower = t.toLowerCase(Locale.US);
        if (looksLikeBlockedPage(t)) return true;
        if (lower.contains("authenticationrequired") || lower.contains("authentication is required")) return true;
        if (lower.contains("anomaly-modal") || lower.contains("bots use duckduckgo")) return true;
        return !containsConcreteFact(t);
    }

    /** Price questions need a $ / € / £ amount, not a YouTube title with a year. */
    public static boolean searchResultsLackPriceFacts(String result) {
        if (searchResultsLackFacts(result)) return true;
        return !containsPriceAmount(result);
    }

    public static boolean isLowValueSearchUrl(String url) {
        String l = url == null ? "" : url.toLowerCase(Locale.US);
        return l.contains("youtube.com") || l.contains("youtu.be") || l.contains("tiktok.com")
                || l.contains("instagram.com") || l.contains("facebook.com") || l.contains("/shorts/")
                || l.contains("duckduckgo.com/y.js") || l.contains("ad_provider=");
    }

    public static String refineSearchQuery(String query, int attempt) {
        String q = query == null ? "" : query.trim();
        if (q.length() == 0) return q;
        String lower = q.toLowerCase(Locale.US);
        if (attempt <= 0) {
            if (looksLikePriceQuery(q) && !lower.contains("usd") && !lower.contains("newegg")) {
                return q + " current price USD";
            }
            return q;
        }
        if (attempt == 1) {
            String stripped = q.replaceAll("(?i)\\b(what|what's|whats|how much|is|are|do|does|the|for|a|an)\\b", " ");
            stripped = stripped.replaceAll("\\s+", " ").trim();
            if (stripped.length() == 0) stripped = q;
            return stripped + " Newegg Amazon kit";
        }
        if (lower.contains("-youtube")) return q;
        return q + " -youtube";
    }

    public static boolean looksLikeBlockedPage(String body) {
        String l = body == null ? "" : body.toLowerCase(Locale.US);
        if (l.length() == 0) return false;
        if (l.contains("anomaly-modal") || l.contains("bots use duckduckgo")) return true;
        if (l.contains("just a moment") && (l.contains("challenge") || l.contains("cloudflare") || l.contains("_cf_chl"))) return true;
        if (l.contains("enable javascript and cookies to continue")) return true;
        if (l.contains("authenticationrequired") || l.contains("authentication is required")) return true;
        if (l.contains("unusual traffic from your computer") || l.contains("are you a robot")) return true;
        return false;
    }

    public static String duckDuckGoTargetUrl(String href) {
        if (href == null) return "";
        String h = href.trim();
        int at = h.indexOf("uddg=");
        if (at >= 0) {
            String rest = h.substring(at + 5);
            int amp = rest.indexOf('&');
            if (amp >= 0) rest = rest.substring(0, amp);
            try {
                return java.net.URLDecoder.decode(rest, "UTF-8").trim();
            } catch (Exception ignored) {
                return rest.trim();
            }
        }
        if (h.startsWith("http://") || h.startsWith("https://")) return h;
        if (h.startsWith("//")) return "https:" + h;
        return "";
    }

    public static String parseDuckDuckGoHtml(String html) {
        if (html == null || html.length() == 0) return "";
        if (looksLikeBlockedPage(html)) return "";
        String serp = parseDuckDuckGoSerp(html);
        if (serp.length() > 0) return serp;
        return parseDuckDuckGoLite(html);
    }

    private static String parseDuckDuckGoSerp(String html) {
        String[] parts = html.split("class=\"result ");
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (int i = 1; i < parts.length && n < 8; i++) {
            String p = parts[i];
            if (p.contains("result--ad") || p.startsWith("results_links_deep result--ad")) continue;
            java.util.regex.Matcher titleM = java.util.regex.Pattern.compile("class=\"result__a\"[^>]*>(.*?)</a>", java.util.regex.Pattern.DOTALL).matcher(p);
            java.util.regex.Matcher hrefM = java.util.regex.Pattern.compile("class=\"result__a\"[^>]*href=\"([^\"]+)\"").matcher(p);
            java.util.regex.Matcher snipM = java.util.regex.Pattern.compile("class=\"result__snippet\"[^>]*>(.*?)</", java.util.regex.Pattern.DOTALL).matcher(p);
            String title = titleM.find() ? stripHtml(titleM.group(1)) : "";
            String url = "";
            if (hrefM.find()) url = duckDuckGoTargetUrl(hrefM.group(1).replace("&amp;", "&"));
            String snip = snipM.find() ? stripHtml(snipM.group(1)) : "";
            if (isLowValueSearchUrl(url) || isLowValueSearchUrl(title)) continue;
            if (title.length() == 0 && snip.length() == 0) continue;
            n++;
            out.append(n).append(". ").append(title.length() > 0 ? title : "untitled").append('\n');
            if (url.length() > 0) out.append(url).append('\n');
            if (snip.length() > 0) out.append(snip).append('\n');
            out.append('\n');
        }
        return out.toString().trim();
    }

    private static String parseDuckDuckGoLite(String html) {
        java.util.regex.Matcher a = java.util.regex.Pattern.compile("<a([^>]*)>(.*?)</a>", java.util.regex.Pattern.DOTALL).matcher(html);
        StringBuilder out = new StringBuilder();
        int n = 0;
        while (a.find() && n < 8) {
            String attrs = a.group(1);
            if (attrs.indexOf("result-link") < 0) continue;
            String title = stripHtml(a.group(2));
            if (title.length() == 0 || "more info".equalsIgnoreCase(title)) continue;
            String before = html.substring(Math.max(0, a.start() - 280), a.start());
            if (before.contains("result-sponsored") || before.contains("Sponsored link")) continue;
            java.util.regex.Matcher href = java.util.regex.Pattern.compile("href=['\"]([^'\"]+)['\"]").matcher(attrs);
            String url = href.find() ? duckDuckGoTargetUrl(href.group(1).replace("&amp;", "&")) : "";
            if (isLowValueSearchUrl(url) || isLowValueSearchUrl(title)) continue;
            String after = html.substring(a.end(), Math.min(html.length(), a.end() + 1800));
            java.util.regex.Matcher snip = java.util.regex.Pattern.compile("class=['\"]result-snippet['\"][^>]*>(.*?)</td>", java.util.regex.Pattern.DOTALL).matcher(after);
            String snippet = snip.find() ? stripHtml(snip.group(1)) : "";
            n++;
            out.append(n).append(". ").append(title).append('\n');
            if (url.length() > 0) out.append(url).append('\n');
            if (snippet.length() > 0) out.append(snippet).append('\n');
            out.append('\n');
        }
        return out.toString().trim();
    }

    private static String stripHtml(String s) {
        if (s == null) return "";
        String t = s.replaceAll("(?is)<[^>]+>", " ");
        t = t.replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    public static String compactWebSearch(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() == 0) return "";
        String facts = searchSnippetFallback(t);
        StringBuilder out = new StringBuilder();
        if (facts.length() > 0 && !looksLikeSourceMetadataOnly(facts)
                && (containsPriceAmount(facts) || containsConcreteFact(facts))
                && !isLowValueSearchUrl(facts)) {
            out.append("Facts:\n").append(facts).append("\n\n");
        }
        String[] lines = t.replace('\r', '\n').split("\n");
        StringBuilder sources = new StringBuilder();
        int kept = 0;
        int skippedVideo = 0;
        String pendingTitle = "";
        String pendingUrl = "";
        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i].trim() : "";
            boolean end = i == lines.length || line.length() == 0;
            if (!end) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
                    pendingUrl = line.startsWith("www.") ? "https://" + line : line;
                    continue;
                }
                if (lower.startsWith("url source:")) {
                    pendingUrl = line.substring(line.indexOf(':') + 1).trim();
                    continue;
                }
                if (lower.startsWith("title:") || lower.matches("(?i)^\\[\\d+\\]\\s*title\\s*:.*") || lower.matches("(?i)^\\d+[.)]\\s+\\S.*")) {
                    if (pendingTitle.length() > 0 || pendingUrl.length() > 0) {
                        kept += appendCompactHit(sources, pendingTitle, pendingUrl, "", kept, skippedVideo);
                    }
                    pendingTitle = line.replaceFirst("(?i)^(?:\\[\\d+\\]\\s*)?(?:\\d+[.)]\\s*)?(?:title\\s*:\\s*)?", "").trim();
                    pendingUrl = "";
                    continue;
                }
                if (lower.startsWith("description:")) {
                    String d = line.substring("description:".length()).trim();
                    if (isLowValueSearchUrl(pendingUrl)) { skippedVideo++; pendingTitle = ""; pendingUrl = ""; continue; }
                    kept += appendCompactHit(sources, pendingTitle, pendingUrl, d, kept, skippedVideo);
                    pendingTitle = "";
                    pendingUrl = "";
                    continue;
                }
                if (pendingTitle.length() > 0 || pendingUrl.length() > 0) {
                    if (isLowValueSearchUrl(pendingUrl) || isLowValueSearchUrl(pendingTitle)) {
                        skippedVideo++;
                    } else {
                        kept += appendCompactHit(sources, pendingTitle, pendingUrl, line, kept, skippedVideo);
                    }
                    pendingTitle = "";
                    pendingUrl = "";
                }
                continue;
            }
            if (pendingTitle.length() > 0 || pendingUrl.length() > 0) {
                if (isLowValueSearchUrl(pendingUrl) || isLowValueSearchUrl(pendingTitle)) skippedVideo++;
                else kept += appendCompactHit(sources, pendingTitle, pendingUrl, "", kept, skippedVideo);
                pendingTitle = "";
                pendingUrl = "";
            }
        }
        if (sources.length() > 0) {
            out.append("Sources:\n").append(sources.toString().trim()).append('\n');
        } else if (out.length() == 0) {
            return t;
        }
        return out.toString().trim();
    }

    private static int appendCompactHit(StringBuilder sources, String title, String url, String snip, int kept, int skippedVideo) {
        if (kept >= 6) return 0;
        if (isLowValueSearchUrl(url) || isLowValueSearchUrl(title)) return 0;
        if (title.length() == 0 && url.length() == 0 && snip.length() == 0) return 0;
        sources.append(kept + 1).append(". ").append(title.length() > 0 ? title : url).append('\n');
        if (url.length() > 0) sources.append(url).append('\n');
        if (snip.length() > 0) sources.append(snip).append('\n');
        sources.append('\n');
        return 1;
    }

    public static boolean looksLikeHtml(String page) {
        if (page == null) return false;
        String t = page.trim();
        if (t.length() < 8) return false;
        String head = t.length() > 200 ? t.substring(0, 200).toLowerCase(Locale.US) : t.toLowerCase(Locale.US);
        return head.startsWith("<!doctype") || head.startsWith("<html") || head.contains("<div") || head.contains("<p ")
                || head.contains("<head") || head.contains("<body");
    }

    public static String htmlToPlainText(String html) {
        if (html == null || html.length() == 0) return "";
        String s = html;
        s = s.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        s = s.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        s = s.replaceAll("(?is)<noscript[^>]*>.*?</noscript>", " ");
        s = s.replaceAll("(?is)</(p|div|tr|h[1-6]|li|section|article|table|ul|ol)>", "\n");
        s = s.replaceAll("(?is)<br\\s*/?>", "\n");
        s = s.replaceAll("(?is)<[^>]+>", " ");
        s = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&#x27;", "'");
        s = s.replaceAll("[ \\t]+", " ");
        return s.trim();
    }

    public static String extractFactLines(String page, int maxChars) {
        if (page == null || page.trim().length() == 0) return "";
        if (looksLikeBlockedPage(page)) return "";
        String src = looksLikeHtml(page) ? htmlToPlainText(page) : page;
        String[] lines = src.replace('\r', '\n').split("\n");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.length() < 8) continue;
            String lower = t.toLowerCase(Locale.US);
            if (lower.contains("cookie") || lower.contains("sign in") || lower.contains("skip to")
                    || lower.contains("javascript") || lower.startsWith("[](") || lower.startsWith("![image")) {
                continue;
            }
            boolean juicy = containsPriceAmount(t)
                    || lower.matches(".*\\b\\d+(?:\\.\\d+)?\\s*/\\s*gb\\b.*")
                    || (lower.matches(".*\\bcl\\d+\\b.*") && lower.matches(".*\\b(ddr5|mhz|price)\\b.*"));
            if (!juicy) continue;
            if (t.length() > 240) {
                int at = t.indexOf('$');
                if (at < 0) at = t.toLowerCase(Locale.US).indexOf("usd");
                if (at < 0) at = 0;
                int start = Math.max(0, at - 80);
                while (start > 0 && Character.isLetterOrDigit(t.charAt(start))) start--;
                if (start < t.length() && !Character.isLetterOrDigit(t.charAt(start))) start++;
                int end = Math.min(t.length(), at + 160);
                t = t.substring(start, end).trim();
            }
            if (out.length() > 0) out.append('\n');
            out.append(t);
            if (out.length() >= maxChars) break;
        }
        return out.toString().trim();
    }

    public static ArrayList<String> preferReaderUrls(ArrayList<String> urls) {
        ArrayList<String> scored = new ArrayList<String>();
        ArrayList<Integer> scores = new ArrayList<Integer>();
        if (urls == null) return scored;
        for (int i = 0; i < urls.size(); i++) {
            String u = urls.get(i);
            if (u == null || u.length() == 0) continue;
            if (isLowValueSearchUrl(u)) continue;
            String l = u.toLowerCase(Locale.US);
            int s = 1;
            if (l.contains("ramprices") || l.contains("whereismyram") || l.contains("pcpartpicker")
                    || l.contains("camelcamelcamel") || l.contains("tomshardware") || l.contains("techpowerup")) s += 5;
            if (l.contains("newegg") || l.contains("bestbuy") || l.contains("microcenter")) s += 4;
            if (l.contains("amazon.") || l.contains("ebay.") || l.contains("walmart.")) s += 2;
            if (l.contains("price") || l.contains("deal")) s += 2;
            if (l.contains("reddit.com")) s += 2;
            scored.add(u);
            scores.add(Integer.valueOf(s));
        }
        ArrayList<String> out = new ArrayList<String>();
        while (out.size() < 2 && scored.size() > 0) {
            int best = 0;
            for (int i = 1; i < scores.size(); i++) if (scores.get(i).intValue() > scores.get(best).intValue()) best = i;
            out.add(scored.get(best));
            scored.remove(best);
            scores.remove(best);
        }
        return out;
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

    /**
     * Last-resort chat text when the model punts after search.
     * Prefers extracted $ / page-fact lines over an apology.
     */
    public static String searchAnswerFallback(String result) {
        String t = result == null ? "" : result.trim();
        if (t.length() == 0) return "";
        StringBuilder facts = new StringBuilder();
        String[] blocks = t.split("\n\n");
        for (int i = 0; i < blocks.length; i++) {
            String b = blocks[i].trim();
            String lower = b.toLowerCase(Locale.US);
            if (lower.startsWith("facts:") || lower.startsWith("page facts")) {
                String body = b.contains("\n") ? b.substring(b.indexOf('\n') + 1).trim() : b;
                if (containsPriceAmount(body) || containsConcreteFact(body)) {
                    if (facts.length() > 0) facts.append("\n\n");
                    facts.append(body);
                }
            }
        }
        if (facts.length() == 0) {
            String extracted = extractFactLines(t, 900);
            if (containsPriceAmount(extracted)) facts.append(extracted);
        }
        if (facts.length() > 0) {
            String out = facts.toString().trim();
            return out.length() > 1200 ? out.substring(0, 1200).trim() + "…" : out;
        }
        String snip = searchSnippetFallback(t);
        if (containsPriceAmount(snip)) return snip;
        return snip;
    }

    public static String webSearchToolsPrompt() {
        return AgentTools.leanToolsPrompt(true, false, true, false);
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

    /** Hostname for a source URL, without scheme/www/port. */
    public static String sourceHost(String url) {
        if (url == null) return "";
        String u = url.trim();
        int scheme = u.indexOf("://");
        if (scheme >= 0) u = u.substring(scheme + 3);
        int slash = u.indexOf('/');
        if (slash >= 0) u = u.substring(0, slash);
        int at = u.lastIndexOf('@');
        if (at >= 0) u = u.substring(at + 1);
        int colon = u.indexOf(':');
        if (colon >= 0) u = u.substring(0, colon);
        if (u.toLowerCase(Locale.US).startsWith("www.")) u = u.substring(4);
        return u.trim();
    }

    /**
     * Host[:port] for endpoint model cards. Keeps the port so two local servers on the
     * same hostname (tts vs gen) stay distinct. Search favicons still use sourceHost.
     */
    public static String endpointCardHost(String url) {
        if (url == null) return "";
        String u = url.trim();
        int scheme = u.indexOf("://");
        if (scheme >= 0) u = u.substring(scheme + 3);
        int slash = u.indexOf('/');
        if (slash >= 0) u = u.substring(0, slash);
        int at = u.lastIndexOf('@');
        if (at >= 0) u = u.substring(at + 1);
        int colon = u.indexOf(':');
        String host = colon >= 0 ? u.substring(0, colon) : u;
        String port = colon >= 0 ? u.substring(colon) : "";
        if (host.toLowerCase(Locale.US).startsWith("www.")) host = host.substring(4);
        return (host + port).trim();
    }

    public static String sourceLetter(String host) {
        if (host == null) return "?";
        String h = host.trim();
        if (h.length() == 0) return "?";
        return h.substring(0, 1).toUpperCase(Locale.US);
    }

    public static String shortToolDetail(String detail, int max) {
        String d = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ').trim();
        d = d.replaceAll("\\s+", " ");
        int cap = max < 8 ? 8 : max;
        if (d.length() > cap) d = d.substring(0, cap).trim() + "…";
        return d;
    }

    /** Live status while a tool is running — Pi/Hermes wording, same gray wave. */
    public static String toolLiveLabel(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.US).trim();
        if ("web_search".equals(n) || "search".equals(n) || "google".equals(n)) return ensureEllipsis("searching the web");
        if ("fetch".equals(n) || "fetch_content".equals(n) || "read_url".equals(n)) return ensureEllipsis("fetching");
        if ("save_memory".equals(n)) return ensureEllipsis("saving memory");
        if ("remove_memory".equals(n)) return ensureEllipsis("updating memory");
        if (n.length() == 0) return ensureEllipsis("working");
        return ensureEllipsis(n.replace('_', ' '));
    }

    /** Every in-progress status uses the same trailing dots: thinking... / fetching... */
    public static String ensureEllipsis(String label) {
        if (label == null) return "...";
        String t = label.trim();
        if (t.length() == 0) return "...";
        if (t.endsWith("…")) t = t.substring(0, t.length() - 1).trim();
        while (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        t = t.trim();
        if (t.length() == 0) return "...";
        return t + "...";
    }

    /** Completed tool row, Claude Code / Cursor style: `name  detail`. */
    public static String toolDoneLabel(String name, String detail) {
        String n = name == null || name.trim().length() == 0 ? "tool" : name.trim();
        String d = shortToolDetail(detail, 52);
        if ("fetch".equals(n) || "fetch_content".equals(n) || "read_url".equals(n)) {
            String host = sourceHost(d);
            if (host.length() > 0) d = host;
        }
        return d.length() == 0 ? n : n + "  " + d;
    }

    /**
     * Explicit "please go use the web" intent — not every question.
     * /search is usually stripped before this runs; globe/research flags cover that.
     */
    public static boolean wantsWebSearch(String userText) {
        if (userText == null) return false;
        String t = userText.trim().toLowerCase(Locale.US);
        if (t.length() == 0) return false;
        if (t.startsWith("/search") || t.startsWith("/research")) return true;
        if (t.contains("search the web") || t.contains("search online") || t.contains("web search")) return true;
        if (t.matches("(?s).*\\bsearch for\\b.*")) return true;
        if (t.matches("(?s).*\\blook\\s*up\\b.*") || t.contains("lookup")) return true;
        if (t.startsWith("google ") || t.startsWith("search ")) return true;
        return false;
    }

    public static String extractSearchQuery(String userText) {
        if (userText == null) return "";
        String t = userText.trim();
        if (t.length() == 0) return "";
        SlashParse slash = parseSlash(t);
        if (slash != null && ("search".equals(slash.name) || "research".equals(slash.name))) {
            return slash.args == null ? "" : slash.args.trim();
        }
        String stripped = t.replaceFirst(
                "(?i)^(?:please\\s+|can you\\s+|could you\\s+|would you\\s+)?"
                        + "(?:search\\s+the\\s+web(?:\\s+for)?|search\\s+online(?:\\s+for)?|"
                        + "search\\s+for|look\\s*up|google|find(?:\\s+me)?)\\s+",
                "");
        stripped = stripped.replaceFirst("[?!.,]+$", "").trim();
        return stripped.length() > 0 ? stripped : t;
    }

    public static final class SlashCommand {
        public final String name;
        public final String description;
        public final String hint;
        public final boolean takesArgs;
        public SlashCommand(String name, String description) {
            this(name, description, "", false);
        }
        public SlashCommand(String name, String description, String hint, boolean takesArgs) {
            this.name = name;
            this.description = description;
            this.hint = hint == null ? "" : hint;
            this.takesArgs = takesArgs;
        }
        public String paletteName() {
            return "/" + name;
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
            new SlashCommand("search", "search the web", "<query>", true),
            new SlashCommand("research", "search deeper", "<query>", true),
            new SlashCommand("help", "list commands", "", false),
            new SlashCommand("memory", "show memory", "[query]", false),
            new SlashCommand("new", "new chat", "", false),
            new SlashCommand("web", "toggle web search", "", false),
    };

    public static SlashParse parseSlash(String text) {
        String t = text == null ? "" : text.trim();
        if (!t.startsWith("/")) return null;
        if (t.startsWith("//")) return null;
        String body = t.substring(1).trim();
        if (body.length() == 0) return null;
        int sp = body.indexOf(' ');
        String name = (sp < 0 ? body : body.substring(0, sp)).toLowerCase(Locale.US);
        String args = sp < 0 ? "" : body.substring(sp + 1).trim();
        if (name.length() == 0) return null;
        if (!name.matches("[a-z][a-z0-9_]*")) return null;
        for (SlashCommand c : SLASH_COMMANDS) {
            if (c.name.equals(name)) return new SlashParse(name, args);
        }
        return null;
    }

    public static SlashCommand slashByName(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        for (SlashCommand c : SLASH_COMMANDS) {
            if (c.name.equals(n)) return c;
        }
        return null;
    }

    /**
     * Palette while composing a command token.
     * Hide once a known command is complete and they are in args mode (`/search `).
     */
    public static ArrayList<SlashCommand> filterSlashCommands(String raw) {
        ArrayList<SlashCommand> out = new ArrayList<SlashCommand>();
        String t = raw == null ? "" : raw;
        if (!t.startsWith("/") || t.startsWith("//")) return out;
        if (t.indexOf('\n') >= 0) return out;
        String rest = t.substring(1);
        int sp = rest.indexOf(' ');
        if (sp >= 0) return out;
        String partial = rest.toLowerCase(Locale.US);
        for (SlashCommand c : SLASH_COMMANDS) {
            if (partial.length() == 0 || c.name.startsWith(partial)) out.add(c);
        }
        return out;
    }

    public static int slashNameColumnChars() {
        int n = 0;
        for (SlashCommand c : SLASH_COMMANDS) n = Math.max(n, c.paletteName().length());
        return n;
    }

    /** Catalog prefix so a custom endpoint can share an API slug with OpenRouter. */
    public static final String CUSTOM_MODEL_PREFIX = "custom|";

    public static String normalizeEndpoint(String endpoint) {
        String e = endpoint == null ? "" : endpoint.trim();
        while (e.endsWith("/")) e = e.substring(0, e.length() - 1);
        return e;
    }

    /** Stable local id: `custom|<endpoint>|<apiId>`. OpenRouter ids stay unchanged. */
    public static String customModelKey(String endpoint, String apiId) {
        String id = modelApiId(apiId);
        if (id.length() == 0) return "";
        String e = normalizeEndpoint(endpoint);
        if (e.length() == 0) return id;
        return CUSTOM_MODEL_PREFIX + e + "|" + id;
    }

    public static String catalogModelKey(String source, String endpoint, String apiId) {
        String id = apiId == null ? "" : apiId.trim();
        if (id.length() == 0) return "";
        if ("custom".equals(source)) return customModelKey(endpoint, id);
        return isCustomModelKey(id) ? modelApiId(id) : id;
    }

    public static boolean isCustomModelKey(String key) {
        return key != null && key.startsWith(CUSTOM_MODEL_PREFIX);
    }

    /** Id sent to `/chat/completions` — strips the local custom prefix. */
    public static String modelApiId(String key) {
        if (key == null) return "";
        String s = key.trim();
        if (!isCustomModelKey(s)) return s;
        String[] parts = s.split("\\|", 3);
        return parts.length >= 3 ? parts[2].trim() : s.substring(CUSTOM_MODEL_PREFIX.length()).trim();
    }

    public static String customModelEndpoint(String key) {
        if (!isCustomModelKey(key)) return "";
        String[] parts = key.split("\\|", 3);
        return parts.length >= 2 ? normalizeEndpoint(parts[1]) : "";
    }

    /**
     * POST URL for chat completions. Custom models must pass the catalog key
     * (`custom|&lt;endpoint&gt;|&lt;id&gt;`), not the stripped API id from the JSON body.
     * Never returns a protocol-less path like `/chat/completions`.
     */
    public static String chatCompletionsUrl(String source, String model, String openrouterEndpoint,
            String mappedEndpoint) {
        String base;
        if ("custom".equals(source) || isCustomModelKey(model)) {
            base = customModelEndpoint(model);
            if (base.length() == 0) base = normalizeEndpoint(mappedEndpoint);
        } else {
            base = normalizeEndpoint(openrouterEndpoint);
        }
        if (base.length() == 0) return "";
        return base + "/chat/completions";
    }

    /** OpenRouter `/models` defaults to text output, which hides STT/TTS catalogs. */
    public static String modelsListUrl(String endpoint, String outputModality) {
        String base = normalizeEndpoint(endpoint) + "/models";
        String mod = outputModality == null ? "" : outputModality.trim();
        if (mod.length() == 0) return base;
        return base + "?output_modalities=" + mod;
    }

    public static boolean architectureHasModality(JSONObject model, String side, String want) {
        if (model == null || want == null || want.length() == 0) return false;
        JSONObject arch = model.optJSONObject("architecture");
        if (arch == null) return false;
        JSONArray arr = "output".equals(side) ? arch.optJSONArray("output_modalities") : arch.optJSONArray("input_modalities");
        if (arr == null) {
            String modality = arch.optString("modality", "").toLowerCase(Locale.US);
            return modality.contains(want.toLowerCase(Locale.US));
        }
        String need = want.toLowerCase(Locale.US);
        for (int i = 0; i < arr.length(); i++) {
            if (need.equalsIgnoreCase(arr.optString(i, ""))) return true;
        }
        return false;
    }

    public static String voiceSearchHaystack(String id, String name, String description, String extra) {
        StringBuilder b = new StringBuilder();
        if (name != null && name.length() > 0) b.append(name).append('\n');
        if (id != null && id.length() > 0) b.append(id).append('\n');
        if (description != null && description.length() > 0) b.append(description).append('\n');
        if (extra != null && extra.length() > 0) b.append(extra);
        return b.toString();
    }

    public static boolean looksLikeStt(String id, String name, String description) {
        String h = ((id == null ? "" : id) + "\n" + (name == null ? "" : name) + "\n" + (description == null ? "" : description)).toLowerCase(Locale.US);
        return h.contains("whisper") || h.contains("transcrib") || h.contains("transcription") || h.contains("speech-to-text")
                || h.contains("speech to text") || containsWord(h, "asr") || containsWord(h, "stt")
                || h.contains("parakeet") || h.contains("chirp") || h.contains("speech recognition");
    }

    public static boolean looksLikeTts(String id, String name, String description) {
        String h = ((id == null ? "" : id) + "\n" + (name == null ? "" : name) + "\n" + (description == null ? "" : description)).toLowerCase(Locale.US);
        if (looksLikeStt(id, name, description) && h.indexOf("tts") < 0) return false;
        return h.contains("tts") || h.contains("text-to-speech") || h.contains("text to speech")
                || h.contains("speech synthesis") || h.contains("orpheus") || h.contains("kokoro")
                || h.contains("lyria") || h.contains("voice clone") || h.contains("mai-voice")
                || h.contains("csm-1b") || h.contains("fish-audio")
                || (h.contains("speech") && h.contains("aura"));
    }

    public static boolean isSttModel(String model, String meta, boolean taggedStt) {
        if (taggedStt) return true;
        return looksLikeStt(model, "", meta == null ? "" : meta);
    }

    public static boolean isSpeechTtsModel(String model, String meta, boolean taggedSpeech, boolean taggedAudioOut) {
        if (taggedSpeech) return true;
        if (looksLikeTts(model, "", meta == null ? "" : meta)) return true;
        return taggedAudioOut && looksLikeTts(model, "", meta == null ? "" : meta);
    }

    public static boolean isSttSearch(String q) {
        String clean = q == null ? "" : q.toLowerCase(Locale.US).trim();
        if (clean.length() == 0) return false;
        if (clean.equals("stt") || clean.equals("asr") || clean.equals("whisper") || clean.equals("transcribe")
                || clean.equals("transcription") || clean.equals("speech") || clean.equals("audio")) return true;
        return clean.contains("asr") || clean.contains("stt") || clean.contains("whisper") || clean.contains("transcrib")
                || clean.contains("speech-to-text") || clean.contains("speech to text");
    }

    public static boolean isTtsSearch(String q) {
        String clean = q == null ? "" : q.toLowerCase(Locale.US).trim();
        if (clean.length() == 0) return false;
        if (clean.equals("tts") || clean.equals("speech") || clean.equals("voice") || clean.equals("audio") || clean.equals("speak")) return true;
        return clean.contains("tts") || clean.contains("text-to-speech") || clean.contains("text to speech") || clean.contains("speech");
    }

    public static boolean dedicatedSpeechNotChatAudio(String model, String meta, boolean taggedSpeech) {
        if (taggedSpeech) return true;
        return looksLikeTts(model, "", meta == null ? "" : meta);
    }

    public static boolean hasAudioInput(JSONObject model, String id, String name, String description) {
        if (architectureHasModality(model, "input", "audio")) return true;
        if (architectureHasModality(model, "output", "transcription")) return true;
        return looksLikeStt(id, name, description);
    }

    public static boolean hasAudioOutput(JSONObject model, String id, String name, String description) {
        if (architectureHasModality(model, "output", "audio")) return true;
        if (architectureHasModality(model, "output", "speech")) return true;
        return looksLikeTts(id, name, description);
    }

    public static boolean hasTranscriptionOutput(JSONObject model) {
        return architectureHasModality(model, "output", "transcription");
    }

    public static boolean hasSpeechOutput(JSONObject model) {
        return architectureHasModality(model, "output", "speech");
    }

    /** Voices advertised on an OpenRouter (or compatible) model object. */
    public static ArrayList<String> parseSupportedVoices(JSONObject model) {
        ArrayList<String> out = new ArrayList<String>();
        if (model == null) return out;
        JSONArray arr = model.optJSONArray("supported_voices");
        if (arr == null) arr = model.optJSONArray("voices");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            String v = o == null ? arr.optString(i, "") : o.optString("id", o.optString("name", o.optString("voice", "")));
            v = v == null ? "" : v.trim();
            if (v.length() > 0 && !out.contains(v)) out.add(v);
        }
        return out;
    }

    public static String voiceDiscoveryKey(boolean endpointMode, String endpoint, String model) {
        if (endpointMode) return "endpoint:" + (endpoint == null ? "" : endpoint.trim());
        String id = model == null ? "" : model.trim();
        return id.length() == 0 ? "model:" : "model:" + id;
    }

    public static boolean isGenericOpenAiVoice(String voice) {
        String v = voice == null ? "" : voice.trim().toLowerCase(Locale.US);
        return v.equals("alloy") || v.equals("ash") || v.equals("ballad") || v.equals("coral") || v.equals("echo")
                || v.equals("fable") || v.equals("nova") || v.equals("onyx") || v.equals("sage") || v.equals("shimmer")
                || v.equals("verse");
    }

    public static String[] fallbackVoicesForModel(String model) {
        String m = model == null ? "" : model.toLowerCase(Locale.US);
        if (m.contains("grok") && (m.contains("voice") || m.contains("tts"))) {
            return new String[] { "eve", "ara", "rex", "sal", "leo" };
        }
        if (m.contains("kokoro")) {
            return new String[] { "af_heart", "af_alloy", "af_aoede", "af_bella", "af_jessica", "af_kore", "af_nicole",
                    "af_nova", "af_river", "af_sarah", "af_sky", "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam",
                    "am_michael", "am_onyx", "am_puck", "am_santa", "bf_alice", "bf_emma", "bf_isabella", "bf_lily",
                    "bm_daniel", "bm_fable", "bm_george", "bm_lewis" };
        }
        if (m.contains("orpheus")) return new String[] { "tara", "leah", "jess", "leo", "dan", "mia", "zac" };
        if (m.contains("csm-1b") || m.contains("sesame")) {
            return new String[] { "conversational_a", "conversational_b", "read_speech_a", "read_speech_b",
                    "read_speech_c", "read_speech_d" };
        }
        if (m.contains("mai-voice")) {
            return new String[] { "en-US-Harper:MAI-Voice-2", "es-MX-Valeria:MAI-Voice-2", "fr-FR-Soleil:MAI-Voice-2",
                    "de-DE-Klaus:MAI-Voice-2" };
        }
        if (m.contains("voxtral")) {
            return new String[] { "en_paul_neutral", "en_paul_cheerful", "gb_oliver_neutral", "gb_jane_neutral",
                    "fr_marie_neutral" };
        }
        if (m.contains("gemini") && (m.contains("tts") || m.contains("lyria") || m.contains("audio"))) {
            return new String[] { "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe",
                    "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib",
                    "Rasalgethi", "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird",
                    "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat" };
        }
        if (m.contains("openai") || m.contains("gpt-4o") || m.contains("gpt-audio") || m.contains("tts-1")) {
            return new String[] { "alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer",
                    "verse" };
        }
        return new String[0];
    }

    /** Catalog voices first; fallback only when the catalog has none. Custom saved names stay visible. */
    public static ArrayList<String> voiceNamesForModel(String model, ArrayList<String> discovered, String saved) {
        ArrayList<String> out = new ArrayList<String>();
        if (discovered != null) {
            for (int i = 0; i < discovered.size(); i++) {
                String v = discovered.get(i);
                if (v != null && v.trim().length() > 0 && !out.contains(v.trim())) out.add(v.trim());
            }
        }
        if (out.size() == 0) {
            String[] fb = fallbackVoicesForModel(model);
            for (int i = 0; i < fb.length; i++) if (!out.contains(fb[i])) out.add(fb[i]);
        }
        String s = saved == null ? "" : saved.trim();
        if (s.length() > 0) {
            boolean present = false;
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i).equalsIgnoreCase(s)) { present = true; break; }
            }
            if (!present) out.add(0, s);
        }
        return out;
    }

    /**
     * Keep a typed/custom voice. Only replace leftover OpenAI defaults when this model
     * has its own list that does not include them.
     */
    public static String resolveSelectedVoice(String saved, ArrayList<String> names) {
        String v = saved == null ? "" : saved.trim();
        if (names == null || names.size() == 0) {
            if (isGenericOpenAiVoice(v)) return "";
            return v;
        }
        if (v.length() == 0) return names.get(0);
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(v)) return names.get(i);
        }
        if (isGenericOpenAiVoice(v)) return names.get(0);
        return v;
    }

    private static boolean containsWord(String haystack, String word) {
        if (haystack == null || word == null || word.length() == 0) return false;
        int from = 0;
        String w = word.toLowerCase(Locale.US);
        String h = haystack.toLowerCase(Locale.US);
        while (true) {
            int at = h.indexOf(w, from);
            if (at < 0) return false;
            boolean startOk = at == 0 || !isIdentChar(h.charAt(at - 1));
            int end = at + w.length();
            boolean endOk = end >= h.length() || !isIdentChar(h.charAt(end));
            if (startOk && endOk) return true;
            from = at + 1;
        }
    }

    private static boolean isIdentChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    public static boolean isKnownEndpoint(String endpoint, ArrayList<String> known) {
        String n = normalizeEndpoint(endpoint);
        if (n.length() == 0 || known == null) return false;
        for (int i = 0; i < known.size(); i++) {
            if (n.equals(normalizeEndpoint(known.get(i)))) return true;
        }
        return false;
    }

    /**
     * If SharedPreferences still has the real URL for a model that was namespaced
     * onto the wrong (usually first) endpoint, move it to that URL.
     * Does not guess customEndpoints[0].
     */
    public static String repairCustomIdentity(String stored, String mappedEndpoint, ArrayList<String> knownEndpoints) {
        String s = stored == null ? "" : stored.trim();
        if (s.length() == 0) return s;
        String mapped = normalizeEndpoint(mappedEndpoint);
        if (mapped.length() == 0 || !isKnownEndpoint(mapped, knownEndpoints)) return s;
        String api = modelApiId(s);
        if (api.length() == 0) return s;
        String keyEp = customModelEndpoint(s);
        if (keyEp.equals(mapped)) return s;
        if (keyEp.length() == 0) return customModelKey(mapped, api);
        // Only move a key off the first saved endpoint when prefs still have the real URL.
        if (knownEndpoints.size() > 0 && keyEp.equals(normalizeEndpoint(knownEndpoints.get(0)))) {
            return customModelKey(mapped, api);
        }
        return s;
    }

    public static boolean sameCustomIdentity(String a, String b) {
        if (a == null || b == null) return false;
        String left = a.trim();
        String right = b.trim();
        if (left.length() == 0 || right.length() == 0) return false;
        if (left.equals(right)) return true;
        String apiA = modelApiId(left);
        String apiB = modelApiId(right);
        if (apiA.length() == 0 || apiB.length() == 0) return false;
        if (apiA.equals(apiB)) return true;
        if (apiA.equalsIgnoreCase(apiB)) return true;
        if (apiA.endsWith("/" + apiB) || apiB.endsWith("/" + apiA)) return true;
        String shortA = shortModel(left);
        String shortB = shortModel(right);
        if (shortA.length() == 0 || shortB.length() == 0) return false;
        return shortA.equalsIgnoreCase(shortB);
    }

    public static ArrayList<String> endpointsInCatalog(ArrayList<String> catalog) {
        ArrayList<String> out = new ArrayList<String>();
        if (catalog == null) return out;
        for (int i = 0; i < catalog.size(); i++) {
            String ep = customModelEndpoint(catalog.get(i));
            if (ep.length() > 0 && !out.contains(ep)) out.add(ep);
        }
        return out;
    }

    public static String pickIdentityMatch(String stored, ArrayList<String> matches) {
        if (matches == null || matches.size() == 0) return "";
        String s = stored == null ? "" : stored.trim();
        for (int i = 0; i < matches.size(); i++) {
            if (s.equals(matches.get(i))) return matches.get(i);
        }
        String api = modelApiId(s);
        String ep = customModelEndpoint(s);
        if (ep.length() > 0) {
            for (int i = 0; i < matches.size(); i++) {
                String m = matches.get(i);
                if (ep.equals(customModelEndpoint(m)) && api.equals(modelApiId(m))) return m;
            }
            for (int i = 0; i < matches.size(); i++) {
                String m = matches.get(i);
                if (ep.equals(customModelEndpoint(m))) return m;
            }
        }
        if (api.length() > 0) {
            for (int i = 0; i < matches.size(); i++) {
                if (api.equals(modelApiId(matches.get(i)))) return matches.get(i);
            }
        }
        return matches.get(0);
    }

    public static ArrayList<String> catalogMatchesForStored(ArrayList<String> catalog, String stored) {
        ArrayList<String> same = new ArrayList<String>();
        String s = stored == null ? "" : stored.trim();
        if (s.length() == 0 || catalog == null) return same;
        for (int i = 0; i < catalog.size(); i++) {
            String c = catalog.get(i);
            if (c == null || c.length() == 0 || !isCustomModelKey(c)) continue;
            if (sameCustomIdentity(s, c) && !same.contains(c)) same.add(c);
        }
        return same;
    }

    /**
     * Map a stored model id onto a freshly fetched catalog.
     * If the stored key points at an endpoint that does not serve this slug, and
     * exactly one catalog row does, use that row — never the first saved endpoint.
     */
    public static String rebindStoredModel(String stored, ArrayList<String> catalog,
            String mappedEndpoint, ArrayList<String> knownEndpoints) {
        String s = stored == null ? "" : stored.trim();
        if (s.length() == 0) return s;
        if (listHas(catalog, s)) return s;
        String api = modelApiId(s);
        if (api.length() == 0) return s;
        String keyEp = customModelEndpoint(s);
        String ep = normalizeEndpoint(mappedEndpoint);
        if (ep.length() == 0) ep = keyEp;
        if (ep.length() > 0) {
            String keyed = customModelKey(ep, api);
            if (listHas(catalog, keyed)) return keyed;
        }
        ArrayList<String> same = catalogMatchesForStored(catalog, s);
        if (same.size() == 0) return s;
        String picked = pickIdentityMatch(s, same);
        if (picked.length() > 0) return picked;
        return s;
    }

    /**
     * After a live /models fetch: catalog custom keys are ground truth.
     * Drop first-endpoint orphans that that endpoint did not actually list.
     * Keep extra models on endpoints that were not listed this round (404 TTS).
     * Never rewrite the catalog itself.
     */
    public static ArrayList<String> reconcileMyModels(ArrayList<String> mine, ArrayList<String> catalog,
            ArrayList<String> listedEndpoints) {
        ArrayList<String> listed = new ArrayList<String>();
        if (listedEndpoints != null) {
            for (int i = 0; i < listedEndpoints.size(); i++) {
                String ep = normalizeEndpoint(listedEndpoints.get(i));
                if (ep.length() > 0 && !listed.contains(ep)) listed.add(ep);
            }
        }
        if (listed.size() == 0) listed.addAll(endpointsInCatalog(catalog));
        ArrayList<String> out = new ArrayList<String>();
        if (mine != null) {
            for (int i = 0; i < mine.size(); i++) {
                String m = mine.get(i);
                if (m == null) continue;
                m = m.trim();
                if (m.length() == 0 || out.contains(m)) continue;
                if (!isCustomModelKey(m)) out.add(m);
            }
        }
        if (catalog != null) {
            for (int i = 0; i < catalog.size(); i++) {
                String c = catalog.get(i);
                if (c == null) continue;
                c = c.trim();
                if (c.length() == 0 || !isCustomModelKey(c) || out.contains(c)) continue;
                out.add(c);
            }
        }
        if (mine != null) {
            for (int i = 0; i < mine.size(); i++) {
                String m = mine.get(i);
                if (m == null) continue;
                m = m.trim();
                if (m.length() == 0 || !isCustomModelKey(m) || out.contains(m)) continue;
                if (catalogMatchesForStored(catalog, m).size() > 0) continue;
                String ep = customModelEndpoint(m);
                if (!isKnownEndpoint(ep, listed)) out.add(m);
            }
        }
        return out;
    }

    public static String retargetStoredModel(String stored, ArrayList<String> catalog, ArrayList<String> myModels) {
        String s = stored == null ? "" : stored.trim();
        if (s.length() == 0) return s;
        if (listHas(myModels, s) || listHas(catalog, s)) return s;
        String mineHit = pickIdentityMatch(s, catalogMatchesForStored(myModels, s));
        if (mineHit.length() > 0) return mineHit;
        String catHit = pickIdentityMatch(s, catalogMatchesForStored(catalog, s));
        if (catHit.length() > 0) return catHit;
        return s;
    }

    public static ArrayList<String> catalogMatchesForApi(ArrayList<String> catalog, String apiId) {
        ArrayList<String> same = new ArrayList<String>();
        String api = apiId == null ? "" : apiId.trim();
        if (api.length() == 0 || catalog == null) return same;
        for (int i = 0; i < catalog.size(); i++) {
            String c = catalog.get(i);
            if (c == null || c.length() == 0) continue;
            if (isCustomModelKey(c) && api.equals(modelApiId(c)) && !same.contains(c)) same.add(c);
        }
        return same;
    }

    /** Display slug. Does not take the last `/` of a `custom|https://host/v1|id` key. */
    public static String shortModel(String key) {
        String id = modelApiId(key);
        if (id.length() == 0) return "";
        int slash = id.lastIndexOf('/');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }

    public static boolean modelLabelMatches(String key, String label) {
        if (key == null || label == null) return false;
        String t = label.trim();
        if (t.length() == 0) return false;
        return key.equals(t) || shortModel(key).equals(t) || modelApiId(key).equals(t);
    }

    /**
     * Map a displayed slug or stored id onto a catalog key.
     * Exact keys win, then the currently selected model, then custom when OpenRouter
     * is not configured, then OpenRouter.
     */
    public static String resolveModelKey(String label, ArrayList<String> myModels, ArrayList<String> models,
            String selected, boolean hasOpenRouterKey, String customEndpoint) {
        String t = label == null ? "" : label.trim();
        if (t.length() == 0) return t;
        if (listHas(myModels, t) || listHas(models, t)) return t;
        if (isCustomModelKey(t)) return t;
        String sel = selected == null ? "" : selected.trim();
        if (sel.length() > 0 && modelLabelMatches(sel, t)) return sel;
        String customHit = firstLabelMatch(myModels, t, true);
        if (customHit.length() == 0) customHit = firstLabelMatch(models, t, true);
        String orHit = firstLabelMatch(myModels, t, false);
        if (orHit.length() == 0) orHit = firstLabelMatch(models, t, false);
        if (isCustomModelKey(sel) && customHit.length() > 0) return customHit;
        if (customHit.length() > 0 && (orHit.length() == 0 || !hasOpenRouterKey)) return customHit;
        if (orHit.length() > 0) return orHit;
        if (customHit.length() > 0) return customHit;
        if (!hasOpenRouterKey && customEndpoint != null && customEndpoint.trim().length() > 0
                && t.indexOf('|') < 0) {
            return customModelKey(customEndpoint, t);
        }
        return t;
    }

    private static boolean listHas(ArrayList<String> list, String t) {
        return list != null && list.contains(t);
    }

    private static String firstLabelMatch(ArrayList<String> list, String t, boolean custom) {
        if (list == null) return "";
        for (int i = 0; i < list.size(); i++) {
            String m = list.get(i);
            if (m == null || m.length() == 0) continue;
            if (isCustomModelKey(m) != custom) continue;
            if (modelLabelMatches(m, t)) return m;
        }
        return "";
    }

    /** OpenRouter-style `vendor/model` prefix, or empty. */
    public static String modelVendor(String id) {
        String s = modelApiId(id);
        if (s.length() == 0) return "";
        int slash = s.indexOf('/');
        if (slash <= 0) return "";
        String v = s.substring(0, slash).trim();
        if (v.length() == 0) return "";
        if (v.contains(":") || v.contains(".")) return "";
        return v.toLowerCase(Locale.US);
    }

    /**
     * Provider shown in the model list: vendor (`anthropic`, `openai`) for OpenRouter ids,
     * `endpoint` for custom servers.
     */
    public static String modelProviderLabel(String id, String source) {
        if ("custom".equals(source) || isCustomModelKey(id)) return "endpoint";
        String vendor = modelVendor(id);
        if (vendor.length() > 0) return vendor;
        return "openrouter";
    }

    /** Pinned models first (in pin order), then the rest in the original order. */
    public static ArrayList<String> orderedModels(ArrayList<String> models, ArrayList<String> pinned) {
        ArrayList<String> out = new ArrayList<String>();
        if (pinned != null) {
            for (int i = 0; i < pinned.size(); i++) {
                String p = pinned.get(i);
                if (p == null || p.trim().length() == 0) continue;
                String clean = p.trim();
                if (models != null && models.contains(clean) && !out.contains(clean)) out.add(clean);
            }
        }
        if (models != null) {
            for (int i = 0; i < models.size(); i++) {
                String m = models.get(i);
                if (m == null || m.trim().length() == 0) continue;
                String clean = m.trim();
                if (!out.contains(clean)) out.add(clean);
            }
        }
        return out;
    }

    /** Rough token estimate: ~3.15 chars or 1.35× words, whichever is larger. */
    public static int estimateTokens(String s) {
        String text = s == null ? "" : s.trim();
        if (text.length() == 0) return 0;
        int chars = text.length();
        int words = text.split("\\s+").length;
        int byChars = (int) Math.ceil(chars / 3.15);
        int byWords = (int) Math.ceil(words * 1.35);
        return Math.max(1, Math.max(byChars, byWords));
    }

    /**
     * Tokens in a chat-completions request body. Walks JSON so image data URLs
     * are counted as a flat 1200 each instead of the base64 blob.
     */
    public static int estimateRequestTokens(JSONArray arr, JSONArray tools) {
        int t = 24;
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                t += 12 + estimateTokens(o.optString("role", ""));
                Object content = o.opt("content");
                if (content instanceof String) t += estimateTokens((String) content);
                else if (content instanceof JSONArray) {
                    JSONArray parts = (JSONArray) content;
                    for (int j = 0; j < parts.length(); j++) {
                        JSONObject p = parts.optJSONObject(j);
                        if (p == null) continue;
                        String type = p.optString("type", "");
                        if ("text".equals(type)) t += estimateTokens(p.optString("text", ""));
                        else if ("image_url".equals(type)) t += 1200;
                        else t += estimateTokens(p.optString("text", ""));
                    }
                }
                t += estimateTokens(o.optString("tool_call_id", ""));
                JSONArray calls = o.optJSONArray("tool_calls");
                if (calls != null && calls.length() > 0) t += Math.max(24, estimateTokens(calls.toString()) / 4);
            }
        }
        if (tools != null && tools.length() > 0) t += 80 + estimateTokens(tools.toString()) / 6;
        return t;
    }

    public static int usagePromptTokens(JSONObject o) {
        if (o == null) return 0;
        JSONObject usage = o.optJSONObject("usage");
        if (usage == null) return 0;
        int n = usage.optInt("prompt_tokens", 0);
        if (n <= 0) n = usage.optInt("input_tokens", 0);
        if (n <= 0) n = usage.optInt("promptTokens", 0);
        return Math.max(0, n);
    }

    public static String relativeTime(long now, long then) {
        if (then <= 0) return "";
        long ago = now - then;
        if (ago < 0) ago = 0;
        if (ago < 45000L) return "now";
        if (ago < 3600000L) return Math.max(1, ago / 60000L) + "m";
        if (ago < 86400000L) return Math.max(1, ago / 3600000L) + "h";
        if (ago < 7L * 86400000L) return Math.max(1, ago / 86400000L) + "d";
        if (ago < 30L * 86400000L) return Math.max(1, ago / (7L * 86400000L)) + "w";
        return Math.max(1, ago / (30L * 86400000L)) + "mo";
    }

    public static boolean textMatchesQuery(String query, String a, String b) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (q.length() == 0) return true;
        String x = a == null ? "" : a.toLowerCase(Locale.US);
        String y = b == null ? "" : b.toLowerCase(Locale.US);
        return x.contains(q) || y.contains(q);
    }

    public static long recencyMillis(long updatedAt, String id, long lastMessageAt) {
        if (updatedAt > 0) return updatedAt;
        if (lastMessageAt > 0) return lastMessageAt;
        if (id == null) return 0;
        try { return Long.parseLong(id.trim()); } catch (Exception e) { return 0; }
    }

    public static boolean isApkMagic(byte[] head) {
        return isApkMagic(head, head == null ? 0 : head.length);
    }

    public static boolean isApkMagic(byte[] head, int len) {
        return head != null && len >= 2 && head[0] == 'P' && head[1] == 'K';
    }

    public static boolean isHttpRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    public static String resolveRedirectUrl(String currentUrl, String location) {
        if (location == null) return null;
        String loc = location.trim();
        if (loc.length() == 0) return null;
        try {
            return new java.net.URL(new java.net.URL(currentUrl), loc).toString();
        } catch (Exception e) {
            if (loc.startsWith("http://") || loc.startsWith("https://")) return loc;
            return null;
        }
    }

    public static boolean downloadLengthMismatch(long expected, long written) {
        return expected > 0 && written != expected;
    }

    public static boolean isTransientDownloadError(Throwable e) {
        if (e == null) return false;
        String m = (e.getMessage() == null ? "" : e.getMessage()).toLowerCase(Locale.US);
        String c = e.getClass().getName().toLowerCase(Locale.US);
        return m.contains("unexpected end of stream")
                || m.contains("connection reset")
                || m.contains("connection closed")
                || m.contains("software caused connection abort")
                || m.contains("broken pipe")
                || m.contains("timeout")
                || m.contains("timed out")
                || m.contains("failed to connect")
                || m.contains("unable to resolve")
                || m.contains("truncated")
                || c.contains("unknownhost")
                || c.contains("sockettimeout")
                || c.contains("eofexception")
                || c.contains("connectexception");
    }

    public static String friendlyDownloadError(Throwable e) {
        if (e == null) return "download failed";
        String m = (e.getMessage() == null ? "" : e.getMessage()).toLowerCase(Locale.US);
        if (m.contains("unexpected end of stream") || m.contains("truncated") || m.contains("content-length")) {
            return "download cut off - retry";
        }
        if (m.contains("not an apk") || m.contains("html")) {
            return "github sent a webpage instead of the apk - retry";
        }
        if (isTransientDownloadError(e)) {
            return "network dropped - retry";
        }
        String raw = e.getMessage() == null || e.getMessage().trim().length() == 0
                ? e.getClass().getSimpleName()
                : e.getMessage().replace('\n', ' ').trim();
        if (raw.length() > 80) raw = raw.substring(0, 80);
        return "update failed: " + raw;
    }

    public static String[] updateDownloadUrls(String primary, String version) {
        ArrayList<String> urls = new ArrayList<String>();
        addUniqueUrl(urls, primary);
        String tag = version == null ? "" : version.trim();
        if (tag.startsWith("v") || tag.startsWith("V")) tag = tag.substring(1);
        if (tag.length() > 0) {
            addUniqueUrl(urls, "https://github.com/awpsec/lightui/releases/download/v" + tag + "/lightui-release.apk");
        }
        addUniqueUrl(urls, "https://github.com/awpsec/lightui/releases/latest/download/lightui-release.apk");
        return urls.toArray(new String[0]);
    }

    private static void addUniqueUrl(ArrayList<String> urls, String url) {
        if (url == null) return;
        String u = url.trim();
        if (u.length() == 0) return;
        for (int i = 0; i < urls.size(); i++) if (u.equals(urls.get(i))) return;
        urls.add(u);
    }

    public static String updateApkFileName(String version) {
        String v = sanitizeVersion(version);
        if (v.length() == 0) return "lightui-update.apk";
        return "lightui-update-" + v + ".apk";
    }

    public static int versionCodeFromName(String version) {
        String v = sanitizeVersion(version);
        if (v.length() == 0) return 0;
        String[] parts = v.split("\\.");
        int maj = parts.length > 0 ? parseVersionInt(parts[0]) : 0;
        int min = parts.length > 1 ? parseVersionInt(parts[1]) : 0;
        int pat = parts.length > 2 ? parseVersionInt(parts[2]) : 0;
        return maj * 10000 + min * 100 + pat;
    }

    public static String sanitizeVersion(String version) {
        if (version == null) return "";
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.') b.append(c);
        }
        return b.toString();
    }

    static int parseVersionInt(String s) {
        if (s == null || s.length() == 0) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') break;
            n = n * 10 + (c - '0');
        }
        return n;
    }

    /**
     * Progress labels that should not replace the thinking wave in full voice mode.
     * Real errors/prompts (speech failed, need contacts, transcribe failed) stay visible.
     */
    public static boolean isVoiceWaitStatus(String status) {
        String t = status == null ? "" : status.trim().toLowerCase(Locale.US);
        if (t.length() == 0) return true;
        if (t.equals("listening") || t.equals("recording") || t.equals("recording wav")
                || t.equals("transcribing") || t.equals("speaking") || t.equals("responding")
                || t.equals("done") || t.equals("paused") || t.equals("continuing")
                || t.equals("retrying speech")) return true;
        return t.equals("thinking") || t.startsWith("thinking");
    }

    /** In-transcript wait mark while the assistant reply has not started speaking. */
    public static String voiceWaitPulse(int step) {
        int n = 1 + Math.abs(step % 3);
        if (n == 1) return "thinking.";
        if (n == 2) return "thinking..";
        return "thinking...";
    }

    /**
     * SpeechRecognizer onRmsChanged is typically -2..10 dB. Device silence is
     * often 0 dB, not -2, so keep 0 dB idle. Speech starts around 2..4.
     * Visual only — do not use this for silence/speech gates.
     */
    public static float voiceVisualFromRmsDb(float rmsdB) {
        return voiceVisualGain((rmsdB - 1.2f) / 7f);
    }

    /** Visual 0..1 from a PCM peak (0..32767). Mic floor ~0..2000 is idle. */
    public static float voiceVisualFromPeak(int peak) {
        return voiceVisualGain((peak - 2000) / 10000f);
    }

    /** Recorder VAD scale. True silence is 0 (no floor). */
    public static float voiceGateFromPeak(int peak) {
        if (peak < 0) peak = 0;
        return Math.max(0f, Math.min(1f, peak / 14000f));
    }

    public static boolean voicePeakIsQuiet(int peak) { return peak < 2400; }

    public static boolean voicePeakIsSpeech(int peak) { return peak > 3500; }

    public static boolean voiceRmsIsQuiet(float rmsdB) { return rmsdB < 1.4f; }

    public static boolean voiceRmsIsSpeech(float rmsdB) { return rmsdB >= 2.2f; }

    /** Mid-band mic floor is not speech, so it must not keep the listen chrome open. */
    public static boolean voiceRmsHoldsListen(float rmsdB) { return voiceRmsIsSpeech(rmsdB); }

    public static boolean voicePeakHoldsListen(int peak) { return voicePeakIsSpeech(peak); }

    public static boolean voiceHeardEnoughSpeech(int speechFrames) { return speechFrames >= 3; }

    public static float followVoiceShown(float shown, float level) {
        float k = level > shown ? 0.72f : 0.38f;
        return shown + (level - shown) * k;
    }

    static float voiceVisualGain(float normalized) {
        float n = normalized;
        if (n < 0f) n = 0f;
        if (n > 1f) n = 1f;
        if (n <= 0.02f) return 0.02f;
        n = (float) Math.pow(n, 0.65);
        return n;
    }
}

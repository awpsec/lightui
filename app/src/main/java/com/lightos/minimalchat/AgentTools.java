package com.lightos.minimalchat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pi-style tool harness: a tiny "Available tools" prompt, OpenAI tool schemas,
 * stream accumulation, and a real tool-result loop instead of wipe-and-synth.
 */
public final class AgentTools {
    private AgentTools() {}

    public static final int DEFAULT_MAX_ROUNDS = 4;
    public static final int RESEARCH_MAX_ROUNDS = 6;
    public static final int TOOL_RESULT_CHARS = 4000;

    public static final String WEB_SEARCH = "web_search";
    public static final String FETCH = "fetch";
    public static final String SAVE_MEMORY = "save_memory";
    public static final String REMOVE_MEMORY = "remove_memory";

    public static final class ToolCall {
        public String id = "";
        public String name = "";
        public String arguments = "";

        public boolean isSearch() {
            String n = name == null ? "" : name.toLowerCase(Locale.US);
            return WEB_SEARCH.equals(n) || "google".equals(n) || "search".equals(n);
        }

        public boolean isFetch() {
            String n = name == null ? "" : name.toLowerCase(Locale.US);
            return FETCH.equals(n) || "fetch_content".equals(n) || "read_url".equals(n);
        }

        public boolean isSaveMemory() {
            return SAVE_MEMORY.equalsIgnoreCase(name == null ? "" : name);
        }

        public boolean isRemoveMemory() {
            return REMOVE_MEMORY.equalsIgnoreCase(name == null ? "" : name);
        }

        public boolean isMemory() {
            return isSaveMemory() || isRemoveMemory();
        }

        public String query() {
            String q = jsonField(arguments, "query");
            if (q.length() == 0) q = jsonField(arguments, "q");
            if (q.length() == 0 && arguments != null) {
                String raw = arguments.trim();
                if (raw.length() > 0 && !raw.startsWith("{") && !raw.startsWith("[")) {
                    q = raw.replaceAll("^\"|\"$", "").trim();
                }
            }
            return q;
        }

        public String url() {
            String u = jsonField(arguments, "url");
            if (u.length() == 0) u = jsonField(arguments, "href");
            if (u.length() == 0 && arguments != null) {
                String raw = arguments.trim().replaceAll("^\"|\"$", "");
                if (raw.startsWith("http://") || raw.startsWith("https://")) u = raw;
            }
            return u.trim();
        }

        public String note() {
            String n = jsonField(arguments, "note");
            if (n.length() == 0) n = jsonField(arguments, "item");
            if (n.length() == 0) n = jsonField(arguments, "memory");
            return n;
        }
    }

    public static final class RoundState {
        public final StringBuilder content = new StringBuilder();
        public final StringBuilder reasoning = new StringBuilder();
        public final LinkedHashMap<Integer, ToolCall> toolAcc = new LinkedHashMap<Integer, ToolCall>();
        public String finishReason = "";
        public String error = "";

        public ArrayList<ToolCall> nativeCalls() {
            ArrayList<ToolCall> out = new ArrayList<ToolCall>();
            int i = 0;
            for (ToolCall c : toolAcc.values()) {
                if (c == null || c.name == null || c.name.trim().length() == 0) continue;
                if (c.id == null || c.id.length() == 0) c.id = "call_" + i;
                out.add(c);
                i++;
            }
            return out;
        }
    }

    public static int maxRounds(boolean research) {
        return research ? RESEARCH_MAX_ROUNDS : DEFAULT_MAX_ROUNDS;
    }

    /**
     * Pi-style system prompt: one-line tool snippets, a few guidelines, no XML.
     * {@code textFallback} adds SEARCH: only when the endpoint rejected native tools.
     */
    public static String leanToolsPrompt(boolean search, boolean memory, boolean textFallback, boolean searchAlreadyInjected) {
        StringBuilder b = new StringBuilder();
        b.append("You are a helpful assistant on the user's phone.\n\n");
        b.append("Available tools:\n");
        boolean any = false;
        if (search && !searchAlreadyInjected) {
            b.append("- web_search: Search the public web\n");
            b.append("- fetch: Fetch a URL and return readable text\n");
            any = true;
        }
        if (memory) {
            b.append("- save_memory: Save a durable fact about the user\n");
            b.append("- remove_memory: Remove a previously saved memory\n");
            any = true;
        }
        if (!any) b.append("(none)\n");
        b.append("\nGuidelines:\n");
        b.append("- Be concise\n");
        if (search && searchAlreadyInjected) {
            b.append("- Search results are already in context. Answer from them, or fetch a source URL if you need the page\n");
        } else if (search) {
            b.append("- Use web_search for current facts; fetch a source when you need the page\n");
        }
        if (memory) {
            b.append("- save_memory only for durable facts (name, preferences, constraints)\n");
        }
        b.append("- Never mention tools unless asked\n");
        if (textFallback && search && !searchAlreadyInjected) {
            b.append("\n").append(textSearchFallbackPrompt());
        }
        return b.toString().trim();
    }

    public static String textSearchFallbackPrompt() {
        return "Structured tools are unavailable. If you need the web, end with exactly one line:\nSEARCH: short query";
    }

    public static JSONArray openaiTools(boolean search, boolean memory) {
        JSONArray tools = new JSONArray();
        try {
            if (search) {
                tools.put(functionTool(WEB_SEARCH,
                        "Search the public web. Returns compact titles, URLs, and snippets so you can choose a source.",
                        "query", "Short search query"));
                tools.put(functionTool(FETCH,
                        "Fetch a URL and return readable text (prices, specs, article body).",
                        "url", "http(s) URL to fetch"));
            }
            if (memory) {
                tools.put(functionTool(SAVE_MEMORY,
                        "Save a durable fact about the user for future chats.",
                        "note", "Short third-person note about the user"));
                tools.put(functionTool(REMOVE_MEMORY,
                        "Remove a previously saved memory.",
                        "note", "Memory to remove"));
            }
        } catch (Exception ignored) { }
        return tools;
    }

    private static JSONObject functionTool(String name, String description, String param, String paramDesc) throws Exception {
        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");
        JSONObject props = new JSONObject();
        JSONObject p = new JSONObject();
        p.put("type", "string");
        p.put("description", paramDesc);
        props.put(param, p);
        parameters.put("properties", props);
        parameters.put("required", new JSONArray().put(param));
        JSONObject fn = new JSONObject();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", parameters);
        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("function", fn);
        return tool;
    }

    public static JSONObject completionBody(String model, JSONArray messages, JSONArray tools, boolean stream, boolean lastRound) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", ToolText.modelApiId(model));
        body.put("messages", messages);
        body.put("stream", stream);
        if (tools != null && tools.length() > 0 && !lastRound) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    public static void absorbSseData(RoundState state, String data) {
        if (state == null || data == null) return;
        String trimmed = data.trim();
        if (trimmed.length() == 0 || "[DONE]".equals(trimmed)) return;
        try {
            absorbChunk(state, new JSONObject(trimmed));
        } catch (Exception ignored) { }
    }

    public static void absorbChunk(RoundState state, JSONObject chunk) {
        if (state == null || chunk == null) return;
        JSONObject err = chunk.optJSONObject("error");
        if (err != null) {
            String msg = err.optString("message", err.toString());
            if (msg.length() > 0) state.error = msg;
        }
        JSONArray choices = chunk.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return;
        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) return;
        String fr = choice.optString("finish_reason", "");
        if (fr.length() > 0 && !"null".equals(fr)) state.finishReason = fr;
        JSONObject delta = choice.optJSONObject("delta");
        if (delta != null) absorbDelta(state, delta, false);
        JSONObject message = choice.optJSONObject("message");
        if (message != null) absorbDelta(state, message, true);
    }

    public static void absorbDelta(RoundState state, JSONObject delta, boolean replace) {
        if (state == null || delta == null) return;
        String content = jsonString(delta, "content");
        if (content.length() > 0) {
            if (replace) {
                state.content.setLength(0);
                state.content.append(content);
            } else {
                state.content.append(content);
            }
        }
        JSONArray tcs = delta.optJSONArray("tool_calls");
        if (tcs != null) {
            if (replace) state.toolAcc.clear();
            absorbToolCallsArray(state, tcs);
        }
        JSONObject legacy = delta.optJSONObject("function_call");
        if (legacy != null) {
            ToolCall c = state.toolAcc.get(0);
            if (c == null) {
                c = new ToolCall();
                c.id = "call_0";
                state.toolAcc.put(0, c);
            }
            String name = jsonString(legacy, "name");
            if (name.length() > 0) c.name = name;
            String args = jsonString(legacy, "arguments");
            if (args.length() > 0) c.arguments = (c.arguments == null ? "" : c.arguments) + args;
            else {
                JSONObject argsObj = legacy.optJSONObject("arguments");
                if (argsObj != null) c.arguments = argsObj.toString();
            }
        }
    }

    private static void absorbToolCallsArray(RoundState state, JSONArray tcs) {
        for (int i = 0; i < tcs.length(); i++) {
            JSONObject tc = tcs.optJSONObject(i);
            if (tc == null) continue;
            int idx = tc.has("index") ? tc.optInt("index", i) : i;
            ToolCall c = state.toolAcc.get(idx);
            if (c == null) {
                c = new ToolCall();
                state.toolAcc.put(idx, c);
            }
            String id = jsonString(tc, "id");
            if (id.length() > 0) c.id = id;
            JSONObject fn = tc.optJSONObject("function");
            if (fn == null) {
                String name = jsonString(tc, "name");
                if (name.length() > 0) c.name = name;
                continue;
            }
            String name = jsonString(fn, "name");
            if (name.length() > 0) c.name = name;
            if (fn.has("arguments") && !fn.isNull("arguments")) {
                Object args = fn.opt("arguments");
                if (args instanceof JSONObject) {
                    c.arguments = args.toString();
                } else {
                    String s = String.valueOf(args);
                    if (s.length() > 0 && !"null".equals(s)) {
                        c.arguments = (c.arguments == null ? "" : c.arguments) + s;
                    }
                }
            }
        }
    }

    /**
     * Text-dialect fallback (SEARCH: / XML). Skips search when the sanitized
     * answer is already usable, except brief acks like "Sure." + SEARCH:.
     */
    public static ArrayList<ToolCall> parseTextToolCalls(String raw, String reasoning, boolean usableAnswer) {
        ArrayList<ToolCall> out = new ArrayList<ToolCall>();
        String content = raw == null ? "" : raw;
        String think = reasoning == null ? "" : reasoning;
        String q = ToolText.webSearchToolQuery(content);
        if (q.length() == 0 && !usableAnswer) q = ToolText.webSearchToolQuery(think);
        if (q.length() > 0) {
            String clean = ToolText.sanitizeAssistantText(content);
            boolean briefAck = usableAnswer && clean.length() < 40 && !ToolText.containsConcreteFact(clean);
            if (!usableAnswer || briefAck) {
                ToolCall c = new ToolCall();
                c.id = "call_search";
                c.name = WEB_SEARCH;
                c.arguments = "{\"query\":" + jsonQuote(q) + "}";
                out.add(c);
            }
        }
        String fetchUrl = ToolText.fetchToolUrl(content);
        if (fetchUrl.length() == 0 && !usableAnswer) fetchUrl = ToolText.fetchToolUrl(think);
        if (fetchUrl.length() > 0) {
            ToolCall c = new ToolCall();
            c.id = "call_fetch";
            c.name = FETCH;
            c.arguments = "{\"url\":" + jsonQuote(fetchUrl) + "}";
            out.add(c);
        }
        String save = memoryNoteFromText(content, "save_memory");
        if (save.length() > 0) {
            ToolCall c = new ToolCall();
            c.id = "call_save";
            c.name = SAVE_MEMORY;
            c.arguments = "{\"note\":" + jsonQuote(save) + "}";
            out.add(c);
        }
        String remove = memoryNoteFromText(content, "remove_memory");
        if (remove.length() > 0) {
            ToolCall c = new ToolCall();
            c.id = "call_remove";
            c.name = REMOVE_MEMORY;
            c.arguments = "{\"note\":" + jsonQuote(remove) + "}";
            out.add(c);
        }
        return out;
    }

    public static ArrayList<ToolCall> resolveCalls(RoundState state, String rawContent, String reasoning, boolean usableAnswer) {
        ArrayList<ToolCall> nativeCalls = state == null ? new ArrayList<ToolCall>() : state.nativeCalls();
        if (nativeCalls.size() > 0) return nativeCalls;
        return parseTextToolCalls(rawContent, reasoning, usableAnswer);
    }

    public static boolean continueAfter(ArrayList<ToolCall> executed, boolean usableAnswer, int round, int maxRounds) {
        if (executed == null || executed.size() == 0) return false;
        if (round + 1 >= maxRounds) return false;
        boolean anySearch = false;
        boolean anyFetch = false;
        boolean anyMemory = false;
        for (int i = 0; i < executed.size(); i++) {
            ToolCall c = executed.get(i);
            if (c == null) continue;
            if (c.isSearch()) anySearch = true;
            if (c.isFetch()) anyFetch = true;
            if (c.isMemory()) anyMemory = true;
        }
        if (anySearch || anyFetch) return true;
        if (anyMemory && !usableAnswer) return true;
        return false;
    }

    public static JSONObject assistantNativeMessage(String content, ArrayList<ToolCall> calls) throws Exception {
        JSONObject o = new JSONObject();
        o.put("role", "assistant");
        o.put("content", content == null ? "" : content);
        o.put("tool_calls", toolCallsJson(calls));
        return o;
    }

    public static JSONArray toolCallsJson(ArrayList<ToolCall> calls) throws Exception {
        JSONArray arr = new JSONArray();
        if (calls == null) return arr;
        for (int i = 0; i < calls.size(); i++) {
            ToolCall c = calls.get(i);
            if (c == null || c.name == null || c.name.length() == 0) continue;
            JSONObject fn = new JSONObject();
            fn.put("name", c.name);
            fn.put("arguments", c.arguments == null || c.arguments.length() == 0 ? "{}" : c.arguments);
            JSONObject tc = new JSONObject();
            tc.put("id", (c.id == null || c.id.length() == 0) ? ("call_" + i) : c.id);
            tc.put("type", "function");
            tc.put("function", fn);
            arr.put(tc);
        }
        return arr;
    }

    public static JSONObject toolResultMessage(String toolCallId, String content) throws Exception {
        JSONObject o = new JSONObject();
        o.put("role", "tool");
        o.put("tool_call_id", toolCallId == null || toolCallId.length() == 0 ? "call_0" : toolCallId);
        o.put("content", clipResult(content));
        return o;
    }

    public static JSONObject textResultUserMessage(String toolName, String query, String result) throws Exception {
        JSONObject o = new JSONObject();
        StringBuilder b = new StringBuilder();
        b.append("Tool result (").append(toolName == null ? "tool" : toolName).append(")");
        if (query != null && query.trim().length() > 0) b.append(" for: ").append(query.trim());
        b.append("\n\n").append(clipResult(result));
        o.put("role", "user");
        o.put("content", b.toString());
        return o;
    }

    public static String clipResult(String result) {
        String s = result == null ? "" : result;
        if (s.length() > TOOL_RESULT_CHARS) s = s.substring(0, TOOL_RESULT_CHARS);
        return s;
    }

    public static boolean looksLikeToolsUnsupported(String error) {
        String l = error == null ? "" : error.toLowerCase(Locale.US);
        if (l.length() == 0) return false;
        if (l.contains("does not support tool") || l.contains("doesn't support tool")
                || l.contains("tools are not supported") || l.contains("tool calling is not")
                || l.contains("function calling is not") || l.contains("unknown tool")
                || l.contains("unsupported tool")) {
            return true;
        }
        boolean mentionsTools = l.contains("\"tools\"") || l.contains("'tools'")
                || l.contains(" tools") || l.contains("tool_choice") || l.contains("tool_calls")
                || l.contains("function_call");
        if (!mentionsTools) return false;
        return l.contains("unexpected") || l.contains("unknown field") || l.contains("unknown argument")
                || l.contains("extra inputs") || l.contains("not valid") || l.contains("invalid")
                || l.contains("unrecognized") || l.contains("not allowed") || l.contains("400");
    }

    public static String jsonField(String raw, String key) {
        if (raw == null || key == null || key.length() == 0) return "";
        String s = raw.trim();
        if (s.length() == 0) return "";
        try {
            JSONObject o = new JSONObject(s);
            String v = o.optString(key, "");
            return v == null || "null".equals(v) ? "" : v.trim();
        } catch (Exception ignored) { }
        String quoted = "\"" + key + "\"";
        int at = s.toLowerCase(Locale.US).indexOf(quoted.toLowerCase(Locale.US));
        if (at < 0) {
            at = s.toLowerCase(Locale.US).indexOf(key.toLowerCase(Locale.US) + "=");
            if (at < 0) return "";
            at += key.length() + 1;
        } else {
            at = s.indexOf(':', at + quoted.length());
            if (at < 0) return "";
            at++;
        }
        while (at < s.length() && Character.isWhitespace(s.charAt(at))) at++;
        if (at >= s.length()) return "";
        if (s.charAt(at) == '"' || s.charAt(at) == '\'') {
            char q = s.charAt(at);
            int end = s.indexOf(q, at + 1);
            if (end < 0) end = s.length();
            return s.substring(at + 1, end).trim();
        }
        int end = at;
        while (end < s.length() && s.charAt(end) != ',' && s.charAt(end) != '}' && s.charAt(end) != '\n') end++;
        return s.substring(at, end).replaceAll("[\"']", "").trim();
    }

    static String memoryNoteFromText(String text, String fnName) {
        String s = text == null ? "" : text;
        String lower = s.toLowerCase(Locale.US);
        if (!lower.contains(fnName.toLowerCase(Locale.US))) return "";
        java.util.regex.Matcher param = java.util.regex.Pattern.compile(
                "(?is)<parameter(?:\\s+name\\s*=\\s*[\"']?(?:note|item|memory)[\"']?|\\s*=\\s*(?:note|item|memory))[^>]*>(.*?)</parameter>").matcher(s);
        if (param.find()) {
            String note = param.group(1).trim();
            if (note.length() > 0) return note;
        }
        String[] markers = new String[]{"<parameter=note>", "\"note\":", "note:", "note="};
        for (int i = 0; i < markers.length; i++) {
            String marker = markers[i];
            int at = lower.indexOf(marker);
            if (at < 0) continue;
            int start = at + marker.length();
            while (start < s.length() && Character.isWhitespace(s.charAt(start))) start++;
            int end = s.length();
            String[] stops = new String[]{"</parameter>", "</function>", "</tool_call>", "\n"};
            for (int j = 0; j < stops.length; j++) {
                int cut = lower.indexOf(stops[j], start);
                if (cut >= 0) end = Math.min(end, cut);
            }
            String note = s.substring(start, end).replace("\"", "").trim();
            if (note.length() > 0) return note;
        }
        return "";
    }

    private static String jsonString(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return "";
        String v = o.optString(key, "");
        return v == null || "null".equals(v) ? "" : v;
    }

    private static String jsonQuote(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == '"') b.append('\\');
            if (ch == '\n') { b.append("\\n"); continue; }
            if (ch == '\r') { b.append("\\r"); continue; }
            b.append(ch);
        }
        b.append('"');
        return b.toString();
    }

    /** Visible for tests — approximate token count of the lean prompt. */
    public static int promptTokenEstimate(String prompt) {
        if (prompt == null || prompt.length() == 0) return 0;
        return Math.max(1, prompt.length() / 4);
    }
}

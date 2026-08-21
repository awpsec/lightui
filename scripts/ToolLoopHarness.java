package com.lightos.minimalchat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Executors;

/**
 * End-to-end loop against a mock OpenAI-compatible server:
 * native tool_calls, 400-on-tools → SEARCH: fallback, and "don't wipe a good answer".
 */
public class ToolLoopHarness {
    private static int failed = 0;

    private static void assertTrue(String name, boolean v) {
        if (!v) { System.err.println("FAIL " + name); failed++; }
        else System.out.println("ok   " + name);
    }

    public static void main(String[] args) throws Exception {
        testNativeToolLoop();
        testTextFallbackAfterToolsRejected();
        testUsableAnswerNotResearched();
        testNativeFetchLoop();
        if (failed > 0) { System.err.println(failed + " failed"); System.exit(1); }
        System.out.println("all harness passed");
    }

    private static void testNativeToolLoop() throws Exception {
        MockServer mock = MockServer.start("native");
        try {
            String answer = runLoop(mock.url, true, "What do DDR5-6000 64GB kits cost?");
            assertTrue("native loop answers from tool result",
                    answer.contains("280") || answer.contains("360") || answer.contains("$"));
            assertTrue("native loop performed a search", mock.searches == 1);
            assertTrue("native loop made a follow-up completion", mock.completions >= 2);
        } finally {
            mock.stop();
        }
    }

    private static void testTextFallbackAfterToolsRejected() throws Exception {
        MockServer mock = MockServer.start("reject-tools");
        try {
            String answer = runLoop(mock.url, true, "What do DDR5-6000 64GB kits cost?");
            assertTrue("fallback answers from SEARCH: path",
                    answer.contains("280") || answer.contains("360") || answer.contains("$"));
            assertTrue("fallback used text SEARCH:", mock.sawSearchDialect);
            assertTrue("fallback did not keep sending tools after 400", mock.toolsAfterReject == 0);
        } finally {
            mock.stop();
        }
    }

    private static void testUsableAnswerNotResearched() throws Exception {
        MockServer mock = MockServer.start("already-answered");
        try {
            String answer = runLoop(mock.url, true, "What do DDR5 kits cost?");
            assertTrue("kept the in-stream answer", answer.contains("$280"));
            assertTrue("did not search when answer already usable", mock.searches == 0);
        } finally {
            mock.stop();
        }
    }

    private static void testNativeFetchLoop() throws Exception {
        MockServer mock = MockServer.start("fetch");
        try {
            String answer = runLoop(mock.url, true, "What do DDR5-6000 64GB kits cost?");
            assertTrue("fetch loop answers from page",
                    answer.contains("259") || answer.contains("10.77") || answer.contains("$"));
            assertTrue("fetch loop searched", mock.searches >= 1);
            assertTrue("fetch loop fetched a page", mock.fetches >= 1);
        } finally {
            mock.stop();
        }
    }

    private static String runLoop(String base, boolean tryNative, String userText) throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject().put("role", "system").put("content",
                AgentTools.leanToolsPrompt(true, false, true, false)));
        arr.put(new JSONObject().put("role", "user").put("content", userText));
        JSONArray tools = AgentTools.openaiTools(true, false);
        boolean nativeTools = tryNative;
        int maxRounds = AgentTools.maxRounds(false);
        String finalAnswer = "";
        ArrayList<String> seen = new ArrayList<String>();
        for (int round = 0; round < maxRounds; round++) {
            boolean lastRound = round == maxRounds - 1;
            JSONObject req = AgentTools.completionBody("mock", arr, nativeTools ? tools : null, true, lastRound);
            PostResult pr;
            try {
                pr = post(base + "/chat/completions", req);
            } catch (RuntimeException e) {
                if (nativeTools && AgentTools.looksLikeToolsUnsupported(e.getMessage())) {
                    nativeTools = false;
                    round--;
                    continue;
                }
                throw e;
            }
            String visible = ToolText.sanitizeAssistantText(pr.state.content.toString());
            boolean usable = ToolText.isUsableFollowupAnswer(visible);
            ArrayList<AgentTools.ToolCall> calls = AgentTools.resolveCalls(
                    pr.state, pr.state.content.toString(), pr.state.reasoning.toString(), usable);
            if (calls.size() == 0) {
                finalAnswer = visible;
                break;
            }
            boolean nativeThisRound = nativeTools && pr.state.nativeCalls().size() > 0;
            if (nativeThisRound) arr.put(AgentTools.assistantNativeMessage(pr.state.content.toString(), calls));
            else arr.put(new JSONObject().put("role", "assistant").put("content", visible.length() > 0 ? visible : pr.state.content.toString()));
            ArrayList<AgentTools.ToolCall> executed = new ArrayList<AgentTools.ToolCall>();
            for (int i = 0; i < calls.size(); i++) {
                AgentTools.ToolCall call = calls.get(i);
                if (call.isFetch()) {
                    String url = call.url();
                    String result = "Median $10.77/GB. Corsair Dominator 64GB DDR5-6000 CL30 about $259.99 at Newegg.";
                    if (nativeThisRound) arr.put(AgentTools.toolResultMessage(call.id, result));
                    else arr.put(AgentTools.textResultUserMessage("fetch", url, result));
                    executed.add(call);
                    continue;
                }
                if (!call.isSearch()) continue;
                String q = call.query();
                if (seen.contains(q.toLowerCase())) continue;
                seen.add(q.toLowerCase());
                String result = "1. Best 64GB DDR5 RAM\nhttps://rampricesusa.com/best-64gb-ddr5-ram\nDual-stick kits ranked by $/GB\n";
                if (nativeThisRound) arr.put(AgentTools.toolResultMessage(call.id, result));
                else arr.put(AgentTools.textResultUserMessage("web_search", q, result));
                executed.add(call);
            }
            if (!AgentTools.continueAfter(executed, usable, round, maxRounds)) {
                finalAnswer = visible;
                break;
            }
            finalAnswer = visible;
        }
        return finalAnswer;
    }

    private static final class PostResult {
        AgentTools.RoundState state;
    }

    private static PostResult post(String url, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "text/event-stream, application/json");
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        int code = c.getResponseCode();
        if (code >= 400) {
            InputStream es = c.getErrorStream();
            String err = es == null ? ("HTTP " + code) : readAll(es);
            throw new RuntimeException(err);
        }
        PostResult pr = new PostResult();
        pr.state = new AgentTools.RoundState();
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) break;
            AgentTools.absorbSseData(pr.state, data);
        }
        br.close();
        return pr;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private static final class MockServer {
        final HttpServer server;
        final String url;
        final String mode;
        int completions = 0;
        int searches = 0;
        int fetches = 0;
        int toolsAfterReject = 0;
        boolean rejectedOnce = false;
        boolean sawSearchDialect = false;

        static MockServer start(String mode) throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            MockServer mock = new MockServer(server, mode);
            server.createContext("/chat/completions", mock::handle);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return mock;
        }

        MockServer(HttpServer server, String mode) {
            this.server = server;
            this.mode = mode;
            this.url = "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void stop() { server.stop(0); }

        void handle(HttpExchange ex) {
            try {
                String req = readAll(ex.getRequestBody());
                completions++;
                JSONObject body = new JSONObject(req);
                boolean hasTools = body.has("tools");
                boolean hasToolRole = body.toString().contains("\"role\":\"tool\"");
                boolean hasTextSearch = body.toString().contains("Tool result (web_search)");
                boolean hasTextFetch = body.toString().contains("Tool result (fetch)");
                boolean hasFetchResult = hasTextFetch || body.toString().contains("Median $10.77");
                boolean hasSearchResult = hasTextSearch || body.toString().contains("rampricesusa.com");
                if (req.contains("SEARCH:")) sawSearchDialect = true;

                if ("reject-tools".equals(mode) && hasTools) {
                    rejectedOnce = true;
                    byte[] err = "{\"error\":{\"message\":\"unexpected field: tools\"}}".getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", "application/json");
                    ex.sendResponseHeaders(400, err.length);
                    ex.getResponseBody().write(err);
                    ex.close();
                    return;
                }
                if ("reject-tools".equals(mode) && rejectedOnce && hasTools) toolsAfterReject++;

                String sse;
                if ("already-answered".equals(mode)) {
                    sse = sseContent("Typical DDR5-6000 64GB kits are about $280–$360 today.\nSEARCH: ddr5 6000 price");
                } else if ("fetch".equals(mode) && (hasFetchResult)) {
                    fetches++;
                    sse = sseContent("64GB DDR5-6000 CL30 kits are about $259.99 right now.");
                } else if ("fetch".equals(mode) && (hasToolRole || hasSearchResult)) {
                    searches++;
                    sse = sseFetchCall("https://rampricesusa.com/best-64gb-ddr5-ram");
                } else if (hasToolRole || hasTextSearch) {
                    searches++;
                    sse = sseContent("Typical DDR5-6000 64GB kits are about $280-$360 today.");
                } else if (hasTools) {
                    sse = sseToolCall("ddr5 6000 64gb price");
                } else {
                    sawSearchDialect = true;
                    sse = sseContent("Sure.\nSEARCH: ddr5 6000 64gb price");
                }
                byte[] out = sse.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/event-stream");
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            } catch (Exception e) {
                try { ex.sendResponseHeaders(500, 0); ex.close(); } catch (Exception ignored) { }
            }
        }

        static String sseContent(String text) throws Exception {
            JSONObject delta = new JSONObject().put("content", text);
            JSONObject chunk = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("delta", delta)));
            JSONObject done = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("delta", new JSONObject()).put("finish_reason", "stop")));
            return "data: " + chunk + "\n\n" + "data: " + done + "\n\n" + "data: [DONE]\n\n";
        }

        static String sseToolCall(String query) throws Exception {
            JSONObject first = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("delta",
                    new JSONObject().put("tool_calls", new JSONArray().put(new JSONObject()
                            .put("index", 0).put("id", "call_1").put("type", "function")
                            .put("function", new JSONObject().put("name", "web_search").put("arguments", "")))))));
            JSONObject second = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("delta",
                    new JSONObject().put("tool_calls", new JSONArray().put(new JSONObject()
                            .put("index", 0)
                            .put("function", new JSONObject().put("arguments", "{\"query\":\"" + query + "\"}")))))));
            JSONObject done = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                    .put("delta", new JSONObject()).put("finish_reason", "tool_calls")));
            return "data: " + first + "\n\n" + "data: " + second + "\n\n" + "data: " + done + "\n\n" + "data: [DONE]\n\n";
        }

        static String sseFetchCall(String url) throws Exception {
            JSONObject first = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("delta",
                    new JSONObject().put("tool_calls", new JSONArray().put(new JSONObject()
                            .put("index", 0).put("id", "call_2").put("type", "function")
                            .put("function", new JSONObject().put("name", "fetch").put("arguments", "")))))));
            JSONObject second = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("delta",
                    new JSONObject().put("tool_calls", new JSONArray().put(new JSONObject()
                            .put("index", 0)
                            .put("function", new JSONObject().put("arguments", "{\"url\":\"" + url + "\"}")))))));
            JSONObject done = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                    .put("delta", new JSONObject()).put("finish_reason", "tool_calls")));
            return "data: " + first + "\n\n" + "data: " + second + "\n\n" + "data: " + done + "\n\n" + "data: [DONE]\n\n";
        }
    }
}

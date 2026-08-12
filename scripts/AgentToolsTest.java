package com.lightos.minimalchat;

import org.json.JSONArray;
import org.json.JSONObject;

public class AgentToolsTest {
    private static int failed = 0;

    private static void assertTrue(String name, boolean v) {
        if (!v) { System.err.println("FAIL " + name); failed++; }
        else System.out.println("ok   " + name);
    }

    private static void assertEq(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            System.err.println("FAIL " + name + "\n  expected: " + expected + "\n  actual:   " + actual);
            failed++;
        } else System.out.println("ok   " + name);
    }

    public static void main(String[] args) throws Exception {
        String lean = AgentTools.leanToolsPrompt(true, true, true, false);
        assertTrue("lean lists web_search", lean.contains("web_search"));
        assertTrue("lean lists save_memory", lean.contains("save_memory"));
        assertTrue("lean has SEARCH fallback", lean.contains("SEARCH:"));
        assertTrue("lean has no XML tutorial", !lean.contains("<function=") && !lean.contains("<tool_call>"));
        assertTrue("lean prompt under 400 tokens", AgentTools.promptTokenEstimate(lean) < 400);
        assertTrue("lean prompt under 1600 chars", lean.length() < 1600);

        String injected = AgentTools.leanToolsPrompt(true, true, true, true);
        assertTrue("injected omits SEARCH dialect", !injected.contains("SEARCH:"));
        assertTrue("injected says results already in context", injected.toLowerCase().contains("already in context"));

        JSONArray tools = AgentTools.openaiTools(true, true);
        assertTrue("schema has 3 tools", tools.length() == 3);
        assertEq("first tool is web_search", "web_search",
                tools.getJSONObject(0).getJSONObject("function").getString("name"));
        assertTrue("web_search has query param",
                tools.getJSONObject(0).getJSONObject("function").getJSONObject("parameters")
                        .getJSONObject("properties").has("query"));

        JSONObject body = AgentTools.completionBody("m", new JSONArray(), tools, true, false);
        assertTrue("request sends tools", body.has("tools"));
        assertEq("tool_choice auto", "auto", body.getString("tool_choice"));
        JSONObject last = AgentTools.completionBody("m", new JSONArray(), tools, true, true);
        assertTrue("last round omits tools", !last.has("tools"));

        AgentTools.RoundState st = new AgentTools.RoundState();
        AgentTools.absorbSseData(st, "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_abc\",\"type\":\"function\",\"function\":{\"name\":\"web_search\",\"arguments\":\"\"}}]}}]}");
        AgentTools.absorbSseData(st, "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"query\\\":\\\"ddr5 price\\\"}\"}}]}}]}");
        AgentTools.absorbSseData(st, "{\"choices\":[{\"finish_reason\":\"tool_calls\",\"delta\":{}}]}");
        assertEq("native name", "web_search", st.nativeCalls().get(0).name);
        assertEq("native id", "call_abc", st.nativeCalls().get(0).id);
        assertEq("native query", "ddr5 price", st.nativeCalls().get(0).query());
        assertEq("finish_reason", "tool_calls", st.finishReason);

        AgentTools.RoundState full = new AgentTools.RoundState();
        AgentTools.absorbSseData(full, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"oilers score\\\"}\"}}]}}]}");
        assertEq("non-stream query", "oilers score", full.nativeCalls().get(0).query());

        AgentTools.RoundState legacy = new AgentTools.RoundState();
        JSONObject legacyChunk = new JSONObject()
                .put("choices", new JSONArray().put(new JSONObject().put("delta",
                        new JSONObject().put("function_call",
                                new JSONObject().put("name", "web_search").put("arguments", "{\"query\":\"weather\"}")))));
        AgentTools.absorbChunk(legacy, legacyChunk);
        assertEq("legacy function_call", "weather", legacy.nativeCalls().get(0).query());

        String ack = "Sure.\nSEARCH: ddr5 6000 price";
        java.util.ArrayList<AgentTools.ToolCall> ackCalls = AgentTools.parseTextToolCalls(ack, "", ToolText.isUsableFollowupAnswer(ToolText.sanitizeAssistantText(ack)));
        assertTrue("brief ack+SEARCH becomes a call", ackCalls.size() == 1 && ackCalls.get(0).isSearch());
        assertEq("ack query", "ddr5 6000 price", ackCalls.get(0).query());

        String answered = "Typical DDR5-6000 64GB kits are about $280–$360 today.\nSEARCH: ddr5 6000 price";
        String answeredClean = ToolText.sanitizeAssistantText(answered);
        java.util.ArrayList<AgentTools.ToolCall> keep = AgentTools.parseTextToolCalls(answered, "", ToolText.isUsableFollowupAnswer(answeredClean));
        assertTrue("usable answer+SEARCH does not re-search", keep.size() == 0);

        java.util.ArrayList<AgentTools.ToolCall> xmlMem = AgentTools.parseTextToolCalls(
                "Got it.\n<tool_call><function=save_memory><parameter=note>User prefers dark mode</parameter></function></tool_call>",
                "", true);
        assertTrue("xml save_memory parsed", xmlMem.size() == 1 && xmlMem.get(0).isSaveMemory());
        assertEq("xml memory note", "User prefers dark mode", xmlMem.get(0).note());

        java.util.ArrayList<AgentTools.ToolCall> searchExec = new java.util.ArrayList<AgentTools.ToolCall>();
        AgentTools.ToolCall sc = new AgentTools.ToolCall();
        sc.name = "web_search";
        searchExec.add(sc);
        assertTrue("search continues loop", AgentTools.continueAfter(searchExec, false, 0, 4));
        assertTrue("search stops at max", !AgentTools.continueAfter(searchExec, false, 3, 4));

        java.util.ArrayList<AgentTools.ToolCall> memExec = new java.util.ArrayList<AgentTools.ToolCall>();
        AgentTools.ToolCall mc = new AgentTools.ToolCall();
        mc.name = "save_memory";
        memExec.add(mc);
        assertTrue("memory+usable stops", !AgentTools.continueAfter(memExec, true, 0, 4));
        assertTrue("memory+empty continues", AgentTools.continueAfter(memExec, false, 0, 4));

        JSONObject toolMsg = AgentTools.toolResultMessage("call_abc", "kits are $299");
        assertEq("tool role", "tool", toolMsg.getString("role"));
        assertEq("tool id", "call_abc", toolMsg.getString("tool_call_id"));

        JSONObject userMsg = AgentTools.textResultUserMessage("web_search", "ddr5", "kits are $299");
        assertEq("text fallback role", "user", userMsg.getString("role"));
        assertTrue("text fallback has result", userMsg.getString("content").contains("$299"));

        assertTrue("unsupported: extra tools field", AgentTools.looksLikeToolsUnsupported("unexpected field: tools"));
        assertTrue("unsupported: does not support", AgentTools.looksLikeToolsUnsupported("This model does not support tools"));
        assertTrue("plain 500 is not tools-unsupported", !AgentTools.looksLikeToolsUnsupported("internal server error"));

        AgentTools.ToolCall qAlias = new AgentTools.ToolCall();
        qAlias.arguments = "{\"q\":\"oilers score\"}";
        assertEq("q alias", "oilers score", qAlias.query());

        if (failed > 0) { System.err.println(failed + " failed"); System.exit(1); }
        System.out.println("all AgentTools passed");
    }
}

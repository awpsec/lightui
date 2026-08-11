package com.lightos.minimalchat;

public class ToolTextTest {
    private static int failed = 0;

    private static void assertEq(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            System.err.println("FAIL " + name + "\n  expected: " + expected + "\n  actual:   " + actual);
            failed++;
        } else System.out.println("ok   " + name);
    }

    private static void assertTrue(String name, boolean v) {
        if (!v) { System.err.println("FAIL " + name); failed++; }
        else System.out.println("ok   " + name);
    }

    public static void main(String[] args) {
        String tool = "Sure.\n<tool_call><function=web_search><parameter=query>lakers score</parameter></function></tool_call>";
        assertEq("strip tool call", "Sure.", ToolText.stripToolCalls(tool));
        assertEq("query from tool", "lakers score", ToolText.webSearchToolQuery(tool));
        assertTrue("residue detected", ToolText.looksLikeToolResidue(tool));
        assertTrue("sanitized empty of tools", !ToolText.sanitizeAssistantText(tool).contains("web_search"));

        String incomplete = "Looking that up\n<function=web_search";
        assertEq("strip incomplete function", "Looking that up", ToolText.stripToolCalls(incomplete));
        assertTrue("incomplete is residue", ToolText.looksLikeToolResidue(incomplete));
        assertEq("stream hides incomplete", "Looking that up", ToolText.visibleStreamingAnswer(incomplete));

        String pipe = "ok\n<|tool_call_begin|>web_search\nquery: bitcoin price\n<|tool_call_end|>";
        assertEq("pipe query", "bitcoin price", ToolText.webSearchToolQuery(pipe));
        assertTrue("pipe stripped", !ToolText.stripToolCalls(pipe).contains("tool_call"));

        String jsonish = "{\"name\":\"web_search\",\"arguments\":{\"query\":\"nba finals\"}}";
        assertEq("json query", "nba finals", ToolText.webSearchToolQuery(jsonish));
        assertTrue("json residue", ToolText.looksLikeToolResidue(jsonish));

        String fenced = "```xml\n<tool_call><function=web_search><parameter=query>weather</parameter></function></tool_call>\n```";
        assertTrue("fenced stripped", ToolText.stripToolCalls(fenced).length() == 0);

        String google = "I'll check.\n[google(query=\"oilers score\")]";
        assertEq("google query", "oilers score", ToolText.webSearchToolQuery(google));
        assertEq("google stripped", "I'll check.", ToolText.stripToolCalls(google));

        String planning = "I need to answer using the sources below now.";
        assertTrue("planning detected", ToolText.looksLikeSearchPlanning(planning));
        assertTrue("needs followup after search+planning",
                ToolText.needsSearchFollowup(planning, planning, true));
        assertTrue("no followup without context",
                !ToolText.needsSearchFollowup(planning, planning, false));

        String thinkingBleed = "The search has already been performed. I should answer the user with the score.";
        assertTrue("thinking bleed planning", ToolText.looksLikeSearchPlanning(thinkingBleed));
        assertTrue("thinking not usable followup", !ToolText.isUsableFollowupAnswer(thinkingBleed));

        String real = "The Lakers won 112-108 last night.";
        assertTrue("real answer usable", ToolText.isUsableFollowupAnswer(real));
        assertTrue("real answer not planning", !ToolText.looksLikeSearchPlanning(real));

        String emptyAfterStrip = ToolText.sanitizeAssistantText(tool.replace("Sure.\n", ""));
        assertEq("tool-only becomes empty", "", emptyAfterStrip);
        assertTrue("needs followup for tool-only with context",
                ToolText.needsSearchFollowup(tool.replace("Sure.\n", ""), "", true));

        String messyJsonQuery = "call web_search with \"query\":\"bitcoin price\"}}";
        assertEq("messy json query cleaned", "bitcoin price", ToolText.webSearchToolQuery(messyJsonQuery));

        String proseMentions = "I won't invent a web_search call; here's what I know.";
        assertEq("prose mention not cut by stream", proseMentions, ToolText.visibleStreamingAnswer(proseMentions));

        assertTrue("prompt teaches format", ToolText.webSearchToolsPrompt().contains("<function=web_search>"));

        // DDR5-style Jina dump must not become the chat answer.
        String jina = "[1] Title: The DDR5 Price Crisis: Why RAM Costs So Much in 2026 and How to Buy Smart\n"
                + "URL Source: https://example.com/ddr5\n"
                + "Published Date: 2026-08-01\n"
                + "Description: DDR5-6000 CL30 2x32GB kits are commonly listing around $280-$360 this week, with some spikes higher during shortages.\n"
                + "\n"
                + "[2] Title: Another headline without a body\n"
                + "URL Source: https://example.com/other\n";
        String titleOnly = "From the gathered sources: [1] Title: The DDR5 Price Crisis: Why RAM Costs So Much in 2026 and How to Buy Smart";
        assertTrue("title dump unusable", !ToolText.isUsableFollowupAnswer(titleOnly));
        assertTrue("raw title line is meta", ToolText.isSourceMetaLine("[1] Title: The DDR5 Price Crisis: Why RAM Costs So Much in 2026 and How to Buy Smart"));
        String snippet = ToolText.searchSnippetFallback(jina);
        assertTrue("fallback prefers description", snippet.toLowerCase().contains("280") || snippet.toLowerCase().contains("ddr5"));
        assertTrue("fallback is not a title", !snippet.toLowerCase().contains("title:"));
        assertTrue("followup system asks for facts", ToolText.webSearchFollowupSystem(true).toLowerCase().contains("concrete facts"));

        if (failed > 0) { System.err.println(failed + " failed"); System.exit(1); }
        System.out.println("all passed");
    }
}

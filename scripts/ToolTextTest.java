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

        assertTrue("prompt teaches SEARCH fallback", ToolText.webSearchToolsPrompt().contains("SEARCH:"));
        assertTrue("lean prompt lists web_search", ToolText.webSearchToolsPrompt().contains("web_search"));
        assertTrue("lean prompt has no XML dump", !ToolText.webSearchToolsPrompt().contains("<function=web_search>"));

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

        String empty = "";
        assertTrue("empty needs recovery", ToolText.needsEmptyReplyRecovery("", empty));
        assertTrue("usable answer skips recovery", !ToolText.needsEmptyReplyRecovery(real, real));
        String mono = "The user said Testing. I should respond politely and briefly.";
        assertTrue("monologue detected", ToolText.looksLikeInternalMonologue(mono));
        assertTrue("monologue unusable", !ToolText.isUsableFollowupAnswer(mono));
        assertTrue("monologue needs recovery", ToolText.needsEmptyReplyRecovery(mono, mono));
        assertTrue("empty followup system present", ToolText.emptyReplyFollowupSystem().toLowerCase().contains("empty"));

        // Usable sanitized answer must not be wiped because raw stream was a tool call.
        String toolOnly = "<tool_call><function=web_search><parameter=query>ddr5</parameter></function></tool_call>";
        String goodSearchAnswer = "Typical DDR5-6000 64GB kits are about $280–$360 today.";
        assertTrue("usable answer skips recovery even if raw was tool",
                !ToolText.needsEmptyReplyRecovery(toolOnly, goodSearchAnswer));
        assertTrue("empty sanitized still recovers", ToolText.needsEmptyReplyRecovery(toolOnly, ""));

        String braveish = "1. Some RAM Deal Page\nhttps://example.com/ram\nDDR5 64GB kits listing around $299 this week at major sellers.\n\n";
        String snip = ToolText.searchSnippetFallback(braveish);
        assertTrue("brave desc fallback", snip.toLowerCase().contains("299") || snip.toLowerCase().contains("ddr5"));

        assertTrue("slash filter on /", ToolText.filterSlashCommands("/").size() >= 4);
        assertTrue("slash filter /se", ToolText.filterSlashCommands("/se").size() >= 1);
        assertTrue("slash hide after args", ToolText.filterSlashCommands("/search ddr5").size() == 0);
        assertTrue("parse research", ToolText.parseSlash("/research ddr5 prices") != null
                && "research".equals(ToolText.parseSlash("/research ddr5 prices").name));
        assertTrue("followup attempt2 mentions final", ToolText.webSearchFollowupSystem(true, 2).toLowerCase().contains("final"));

        // SEARCH: dialect for weak/local models
        assertEq("SEARCH query", "ddr5 6000 64gb price", ToolText.webSearchToolQuery("Looking that up.\nSEARCH: ddr5 6000 64gb price"));
        assertEq("SEARCH stripped", "Looking that up.", ToolText.stripToolCalls("Looking that up.\nSEARCH: ddr5 6000 64gb price"));
        assertTrue("SEARCH is residue", ToolText.looksLikeToolResidue("SEARCH: bitcoin price"));

        // Concrete facts veto planning false-positives
        assertTrue("has fact $", ToolText.containsConcreteFact("Based on the sources I found, kits are $299."));
        assertTrue("factful not planning", !ToolText.looksLikeSearchPlanning("Based on the sources I found, kits are $299."));
        assertTrue("pure planning still planning", ToolText.looksLikeSearchPlanning("Now I should use the sources to answer."));
        assertTrue("factful not monologue", !ToolText.looksLikeInternalMonologue("The user asked about RAM; kits are about $300 today."));

        // Stop wiping good answers that also trail a SEARCH:/tool call.
        String answeredPlusSearch = "Typical DDR5-6000 64GB kits are about $280–$360 today.\nSEARCH: ddr5 6000 price";
        String answeredClean = ToolText.sanitizeAssistantText(answeredPlusSearch);
        assertTrue("answer+SEARCH keeps usable body", ToolText.isUsableFollowupAnswer(answeredClean));
        assertTrue("answer+SEARCH does not need followup wipe",
                !ToolText.needsSearchFollowup(answeredPlusSearch, answeredClean, false));
        assertTrue("answer+SEARCH with prior sources still kept",
                !ToolText.needsSearchFollowup(answeredPlusSearch, answeredClean, true));

        // Brief ack + SEARCH still needs the real search path.
        String ackSearch = "Sure.\nSEARCH: ddr5 prices";
        assertTrue("ack+SEARCH needs followup",
                ToolText.needsSearchFollowup(ackSearch, ToolText.sanitizeAssistantText(ackSearch), false));

        // Numbered real answers are not source metadata.
        assertTrue("numbered score usable", ToolText.isUsableFollowupAnswer("1. The Lakers won 112-108 last night."));
        assertTrue("numbered score not meta", !ToolText.isSourceMetaLine("1. The Lakers won 112-108 last night."));
        assertTrue("brave headline still meta", ToolText.isSourceMetaLine("1. Some RAM Deal Page"));

        // Modal "may" is not a date fact; "May 3" is.
        assertTrue("may verb not fact", !ToolText.containsConcreteFact("Prices may vary by seller."));
        assertTrue("May 3 is a date fact", ToolText.containsConcreteFact("Announced on May 3."));

        // Punctuation leftovers after JSON strip are not answers.
        assertTrue("brace junk unusable", !ToolText.isUsableFollowupAnswer("}"));

        // Alternate tool dialects
        assertEq("q dialect", "oilers score", ToolText.webSearchToolQuery("{\"name\":\"web_search\",\"q\":\"oilers score\"}"));
        assertEq("positional dialect", "bitcoin price", ToolText.webSearchToolQuery("web_search(\"bitcoin price\")"));

        // Factful "based on the sources" must stay usable.
        String based = "Based on the sources I checked, 64GB DDR5 kits are typically $280-$360 right now.";
        assertTrue("based-on-sources factful usable", ToolText.isUsableFollowupAnswer(based));
        assertTrue("based-on-sources not planning", !ToolText.looksLikeSearchPlanning(based));

        if (failed > 0) { System.err.println(failed + " failed"); System.exit(1); }
        System.out.println("all passed");
    }
}

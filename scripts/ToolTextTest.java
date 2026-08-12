package com.lightos.minimalchat;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

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
        assertTrue("lean prompt lists fetch", ToolText.webSearchToolsPrompt().contains("fetch:"));
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
        assertEq("FETCH url", "https://rampricesusa.com/best-64gb-ddr5-ram",
                ToolText.fetchToolUrl("Opening it.\nFETCH: https://rampricesusa.com/best-64gb-ddr5-ram"));
        assertEq("FETCH stripped", "Opening it.", ToolText.stripToolCalls("Opening it.\nFETCH: https://rampricesusa.com/best-64gb-ddr5-ram"));

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

        String userPunt = "Based on the search results provided, I dont have specific pricing information for 64GB DDR5 6000 kits. "
                + "The available context only includs a YouTube video from 4 months ago titled "
                + "\"DDR5 RAM Prices are FINALLY Dropping! Buy Now or Wait?\" which suggests that DDR5 RAM prices have been decreasing, "
                + "but doesnt provide specific current pricing data.\n\n"
                + "You should look it up yourself on Newegg or Amazon.";
        assertTrue("user punt detected", ToolText.looksLikeSearchPunt(userPunt));
        assertTrue("user punt unusable", !ToolText.isUsableFollowupAnswer(userPunt));
        assertTrue("priced answer is not a punt", !ToolText.looksLikeSearchPunt(based));

        assertTrue("jina 401 lacks facts", ToolText.searchResultsLackFacts(
                "AuthenticationRequiredError: Authentication is required to use this endpoint."));
        String youtubeDump = "[1] Title: DDR5 RAM Prices are FINALLY Dropping! Buy Now or Wait?\n"
                + "URL Source: https://www.youtube.com/watch?v=abc\n"
                + "Published Date: 2026-04-12\n"
                + "Description: Talking about RAM prices in general.\n";
        assertTrue("youtube year dump lacks price facts", ToolText.searchResultsLackPriceFacts(youtubeDump));
        assertTrue("youtube url is low value", ToolText.isLowValueSearchUrl("https://www.youtube.com/watch?v=abc"));

        String ddgHtml = "<div class=\"result results_links results_links_deep web-result \">"
                + "<h2 class=\"result__title\"><a rel=\"nofollow\" class=\"result__a\" "
                + "href=\"//duckduckgo.com/l/?uddg=https%3A%2F%2Frampricesusa.com%2Fbest-64gb-ddr5-ram&amp;rut=abc\">"
                + "Best 64GB DDR5 RAM</a></h2>"
                + "<div class=\"result__snippet\">Median 64GB kits around $10.77/GB</div></div>"
                + "<div class=\"result results_links result--ad \"><a class=\"result__a\" href=\"https://duckduckgo.com/y.js?ad_provider=x\">Shop Amazon</a>"
                + "<div class=\"result__snippet\">Ad</div></div>";
        String ddgParsed = ToolText.parseDuckDuckGoHtml(ddgHtml);
        assertTrue("ddg keeps ramprices", ddgParsed.contains("rampricesusa.com"));
        assertTrue("ddg drops ads", !ddgParsed.toLowerCase().contains("shop amazon"));
        assertTrue("ddg keeps snippet price", ddgParsed.contains("10.77"));

        String ddgLite = "<tr><td><a rel=\"nofollow\" href=\"//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.newegg.com%2Fp%2Fpl%3Fd%3D64gb&amp;rut=x\" class='result-link'>64gb ddr5 6000 | Newegg.com</a></td></tr>"
                + "<tr><td class='result-snippet'>Search Newegg.com for 64gb ddr5 6000 kits from $299</td></tr>";
        String liteParsed = ToolText.parseDuckDuckGoHtml(ddgLite);
        assertTrue("lite keeps newegg", liteParsed.toLowerCase().contains("newegg"));
        assertTrue("lite keeps price", liteParsed.contains("299"));

        String captcha = "<div class=\"anomaly-modal__title\">Unfortunately, bots use DuckDuckGo too.</div>";
        assertEq("captcha parses empty", "", ToolText.parseDuckDuckGoHtml(captcha));

        String compactYoutube = ToolText.compactWebSearch(youtubeDump + "\n1. Best 64GB DDR5\nhttps://rampricesusa.com/best-64gb-ddr5-ram\nMedian $10.77/GB\n");
        assertTrue("compact drops youtube", !compactYoutube.toLowerCase().contains("youtube.com"));
        assertTrue("compact keeps ramprices", compactYoutube.contains("rampricesusa.com"));

        assertTrue("refine adds usd", ToolText.refineSearchQuery("64GB DDR5 6000 kit price", 0).contains("USD"));
        assertTrue("refine attempt 1 retailers", ToolText.refineSearchQuery("64GB DDR5 6000 kit price", 1).toLowerCase().contains("newegg"));

        ArrayList<String> urls = new ArrayList<String>();
        urls.add("https://www.youtube.com/watch?v=x");
        urls.add("https://www.amazon.com/ddr5");
        urls.add("https://rampricesusa.com/best-64gb-ddr5-ram");
        ArrayList<String> prefer = ToolText.preferReaderUrls(urls);
        assertTrue("prefer ramprices first", prefer.size() > 0 && prefer.get(0).contains("rampricesusa"));

        String ramHtml = "<html><body><h1>Best 64GB DDR5 RAM</h1>"
                + "<p>Median $/GB $10.77/GB From $114.95</p>"
                + "<p>Sweet spot: 64GB (2×32GB) DDR5-6000 CL30 EXPO</p>"
                + "<p>G.SKILL Ripjaws S5 $219.99</p></body></html>";
        String facts = ToolText.extractFactLines(ramHtml, 900);
        assertTrue("html facts have $", facts.contains("10.77") || facts.contains("114.95") || facts.contains("219.99"));
        assertTrue("html facts mention 6000 or CL", facts.toLowerCase().contains("cl30") || facts.contains("6000") || facts.contains("$"));

        String fallback = ToolText.searchAnswerFallback("Page facts (https://rampricesusa.com):\nMedian $10.77/GB · kits from $114.95\n\nSources:\n1. youtube");
        assertTrue("answer fallback uses page facts", fallback.contains("114.95") || fallback.contains("10.77"));

        assertEq("host strips www", "rampricesusa.com", ToolText.sourceHost("https://www.rampricesusa.com/best-64gb-ddr5-ram"));
        assertEq("host ignores port", "newegg.com", ToolText.sourceHost("https://www.newegg.com:443/p/x"));
        assertEq("letter", "R", ToolText.sourceLetter("rampricesusa.com"));
        assertEq("live search", "searching the web...", ToolText.toolLiveLabel("web_search"));
        assertEq("live fetch", "fetching...", ToolText.toolLiveLabel("fetch"));
        assertEq("done search", "web_search  ddr5 64gb 6000", ToolText.toolDoneLabel("web_search", "ddr5 64gb 6000"));
        assertEq("done fetch uses host", "fetch  rampricesusa.com",
                ToolText.toolDoneLabel("fetch", "https://www.rampricesusa.com/best-64gb-ddr5-ram"));
        assertTrue("search for wants web", ToolText.wantsWebSearch("search for ddr5 64gb 6000 price"));
        assertTrue("look up wants web", ToolText.wantsWebSearch("look up the lakers score"));
        assertTrue("/search wants web", ToolText.wantsWebSearch("/search bitcoin"));
        assertTrue("plain question does not force search", !ToolText.wantsWebSearch("what is 2+2"));
        assertTrue("price question does not force search", !ToolText.wantsWebSearch("how much is a 64GB DDR5 6000 kit"));
        assertEq("extract search for", "ddr5 64gb 6000 price", ToolText.extractSearchQuery("search for ddr5 64gb 6000 price"));
        assertEq("extract slash", "bitcoin price", ToolText.extractSearchQuery("/search bitcoin price"));
        assertEq("extract look up", "the lakers score", ToolText.extractSearchQuery("look up the lakers score"));
        assertEq("ellipsis thinking", "thinking...", ToolText.ensureEllipsis("thinking"));
        assertEq("ellipsis already", "fetching...", ToolText.ensureEllipsis("fetching..."));
        assertEq("ellipsis unicode", "searching the web...", ToolText.ensureEllipsis("searching the web…"));
        assertEq("live thinking dots", "searching the web...", ToolText.toolLiveLabel("web_search"));
        assertTrue("palette hides on /search space", ToolText.filterSlashCommands("/search ").size() == 0);
        assertTrue("palette hides on /search query", ToolText.filterSlashCommands("/search ddr5").size() == 0);
        assertTrue("palette shows /se", ToolText.filterSlashCommands("/se").size() == 1
                && "search".equals(ToolText.filterSlashCommands("/se").get(0).name));
        assertTrue("palette aligned names", ToolText.slashNameColumnChars() >= "/research".length());
        assertTrue("path is not a slash command", ToolText.parseSlash("/sdcard/foo") == null);
        assertTrue("unknown slash is not parsed", ToolText.parseSlash("/nope") == null);
        assertTrue("search takes args", ToolText.slashByName("search") != null && ToolText.slashByName("search").takesArgs);
        assertTrue("help has no args", ToolText.slashByName("help") != null && !ToolText.slashByName("help").takesArgs);

        assertEq("vendor from openrouter id", "anthropic", ToolText.modelVendor("anthropic/claude-sonnet-4"));
        assertEq("vendor openai", "openai", ToolText.modelVendor("openai/gpt-4o-mini"));
        assertEq("vendor none for bare id", "", ToolText.modelVendor("gpt-4o-mini"));
        assertEq("vendor none for url", "", ToolText.modelVendor("https://api.example.com/v1"));
        assertEq("provider custom", "endpoint", ToolText.modelProviderLabel("llama3", "custom"));
        assertEq("provider vendor", "anthropic", ToolText.modelProviderLabel("anthropic/claude-sonnet-4", "openrouter"));
        assertEq("provider fallback", "openrouter", ToolText.modelProviderLabel("gpt-4o-mini", "openrouter"));

        ArrayList<String> models = new ArrayList<String>();
        models.add("openai/gpt-4o-mini");
        models.add("anthropic/claude-sonnet-4");
        models.add("google/gemini-2.0-flash-001");
        ArrayList<String> pinned = new ArrayList<String>();
        pinned.add("google/gemini-2.0-flash-001");
        pinned.add("missing/gone");
        pinned.add("anthropic/claude-sonnet-4");
        ArrayList<String> ordered = ToolText.orderedModels(models, pinned);
        assertEq("pin first", "google/gemini-2.0-flash-001", ordered.get(0));
        assertEq("pin second", "anthropic/claude-sonnet-4", ordered.get(1));
        assertEq("rest after pins", "openai/gpt-4o-mini", ordered.get(2));
        assertTrue("pin order size", ordered.size() == 3);

        try {
            JSONArray arr = new JSONArray();
            arr.put(new JSONObject().put("role", "user").put("content", "why is ddr5 expensive"));
            int small = ToolText.estimateRequestTokens(arr, null);
            StringBuilder dump = new StringBuilder();
            for (int i = 0; i < 80; i++) dump.append("[1] Title: The DDR5 Price Crisis\nURL: https://example.com/ddr5\nRAM prices surged in 2026 because of supply.\n\n");
            arr.put(new JSONObject().put("role", "tool").put("tool_call_id", "call_1").put("content", dump.toString()));
            int big = ToolText.estimateRequestTokens(arr, null);
            assertTrue("search dump counted in request", big > small + 800);
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("type", "text").put("text", "see photo"));
            parts.put(new JSONObject().put("type", "image_url").put("image_url",
                    new JSONObject().put("url", "data:image/jpeg;base64," + dump.toString())));
            JSONArray imgArr = new JSONArray();
            imgArr.put(new JSONObject().put("role", "user").put("content", parts));
            int imgTok = ToolText.estimateRequestTokens(imgArr, null);
            assertTrue("image data url not counted as base64", imgTok < 5000);
            JSONObject usage = new JSONObject().put("usage", new JSONObject().put("prompt_tokens", 6120));
            assertTrue("usage prompt tokens", ToolText.usagePromptTokens(usage) == 6120);
        } catch (Exception e) {
            System.err.println("FAIL request token tests: " + e);
            failed++;
        }

        assertEq("relative now", "now", ToolText.relativeTime(100000, 90000));
        assertEq("relative minutes", "3m", ToolText.relativeTime(1000000, 1000000 - 3 * 60000));
        assertEq("relative hours", "2h", ToolText.relativeTime(10000000, 10000000 - 2 * 3600000));
        assertEq("relative months", "3mo", ToolText.relativeTime(10000000000L, 10000000000L - 90L * 86400000L));
        assertTrue("query matches title", ToolText.textMatchesQuery("ddr", "DDR5 prices", "empty chat"));
        assertTrue("query matches preview", ToolText.textMatchesQuery("ram", "untitled", "ai: ram is expensive"));
        assertTrue("empty query matches", ToolText.textMatchesQuery("", "x", "y"));
        assertTrue("query miss", !ToolText.textMatchesQuery("zebra", "cats", "dogs"));
        assertTrue("recency prefers updatedAt", ToolText.recencyMillis(50, "10", 20) == 50);
        assertTrue("recency falls back to message", ToolText.recencyMillis(0, "10", 20) == 20);
        assertTrue("recency falls back to id", ToolText.recencyMillis(0, "12345", 0) == 12345);

        assertTrue("apk magic pk", ToolText.isApkMagic(new byte[] { 'P', 'K', 3, 4 }));
        assertTrue("html not apk", !ToolText.isApkMagic(new byte[] { '<', '!' }));
        assertTrue("short not apk", !ToolText.isApkMagic(new byte[] { 'P' }));
        assertTrue("redirect 302", ToolText.isHttpRedirect(302));
        assertTrue("redirect 307", ToolText.isHttpRedirect(307));
        assertTrue("200 not redirect", !ToolText.isHttpRedirect(200));
        assertEq("absolute redirect",
                "https://release-assets.githubusercontent.com/github-production-release-asset/foo",
                ToolText.resolveRedirectUrl(
                        "https://github.com/awpsec/lightui/releases/download/v1.0.33/lightui-release.apk",
                        "https://release-assets.githubusercontent.com/github-production-release-asset/foo"));
        assertEq("relative redirect",
                "https://github.com/bar",
                ToolText.resolveRedirectUrl("https://github.com/awpsec/lightui/x", "/bar"));
        assertTrue("empty location", ToolText.resolveRedirectUrl("https://github.com/x", "  ") == null);
        assertTrue("length mismatch", ToolText.downloadLengthMismatch(100, 40));
        assertTrue("length match", !ToolText.downloadLengthMismatch(100, 100));
        assertTrue("unknown length ok", !ToolText.downloadLengthMismatch(-1, 40));
        assertTrue("stream cutoff is transient",
                ToolText.isTransientDownloadError(new java.io.IOException("unexpected end of stream on com.android.okhttp.Address@1")));
        assertEq("cutoff toast",
                "download cut off - retry or open in browser",
                ToolText.friendlyDownloadError(new java.io.IOException("unexpected end of stream on com.android.okhttp.Address@1")));
        String[] apkUrls = ToolText.updateDownloadUrls(
                "https://github.com/awpsec/lightui/releases/download/v1.0.33/lightui-release.apk", "v1.0.33");
        assertTrue("primary first", apkUrls.length == 2);
        assertEq("versioned url",
                "https://github.com/awpsec/lightui/releases/download/v1.0.33/lightui-release.apk", apkUrls[0]);
        assertEq("latest url",
                "https://github.com/awpsec/lightui/releases/latest/download/lightui-release.apk", apkUrls[1]);

        if (failed > 0) { System.err.println(failed + " failed"); System.exit(1); }
        System.out.println("all passed");
    }
}

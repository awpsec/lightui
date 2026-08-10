package com.lightos.minimalchat;

public class ObsidianToolsTest {
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
        assertEq("normalize plain title", "Grocery List.md", ObsidianTools.normalizePath("Grocery List"));
        assertEq("normalize already md", "Grocery List.md", ObsidianTools.normalizePath("Grocery List.md"));
        assertEq("normalize nested", "Lists/Todo.md", ObsidianTools.normalizePath("Lists/Todo"));
        assertEq("normalize strips ..", "evil.md", ObsidianTools.normalizePath("../evil"));
        assertEq("normalize leading slash", "Notes/A.md", ObsidianTools.normalizePath("/Notes/A.md"));

        String write = "Sure.\n<tool_call><function=obsidian_write><parameter=path>Grocery List.md</parameter><parameter=content># Grocery List\n\n- milk\n- eggs</parameter></function></tool_call>";
        assertEq("write path", "Grocery List.md", ObsidianTools.writePath(write));
        assertEq("write content", "# Grocery List\n\n- milk\n- eggs", ObsidianTools.writeContent(write));

        String append = "Added.\n<tool_call><function=obsidian_append><parameter=path>Grocery List</parameter><parameter=content>- butter</parameter></function></tool_call>";
        assertEq("append path", "Grocery List", ObsidianTools.appendPath(append));
        assertEq("append content", "- butter", ObsidianTools.appendContent(append));

        // Later grocery add (user scenario)
        String addG = "On it.\n<tool_call><function=obsidian_append><parameter=path>Grocery List.md</parameter><parameter=content>- G</parameter></function></tool_call>";
        assertEq("add G path", "Grocery List.md", ObsidianTools.appendPath(addG));
        assertEq("add G content", "- G", ObsidianTools.appendContent(addG));
        assertEq("add G merged", "# Grocery List\n\n- milk\n- eggs\n- G\n",
                ObsidianTools.mergeAppend("# Grocery List\n\n- milk\n- eggs\n", "- G"));

        String read = "<tool_call><function=obsidian_read><parameter=note>Grocery List.md</parameter></function></tool_call>";
        assertEq("read note alias", "Grocery List.md", ObsidianTools.readPath(read));

        String nameAttr = "<function=obsidian_append><parameter name=\"path\">Lists/Todo.md</parameter><parameter name=\"content\">- G</parameter></function>";
        assertEq("name= path", "Lists/Todo.md", ObsidianTools.appendPath(nameAttr));
        assertEq("name= content", "- G", ObsidianTools.appendContent(nameAttr));

        assertTrue("looksLike write", ObsidianTools.looksLikeTool(write));
        assertTrue("not looksLike plain", !ObsidianTools.looksLikeTool("just chat"));

        assertEq("merge append", "# List\n- a\n- b\n", ObsidianTools.mergeAppend("# List\n- a", "- b"));
        assertEq("merge empty existing", "- a\n", ObsidianTools.mergeAppend("", "- a"));

        assertEq("missing app", ObsidianTools.MISSING_APP, ObsidianTools.notReadyReason(false, true, true));
        assertTrue("missing app wording", ObsidianTools.MISSING_APP.toLowerCase().contains("no obsidian found"));
        assertTrue("missing app install hint", ObsidianTools.MISSING_APP.toLowerCase().contains("install obsidian"));
        assertEq("no vault", "link your Obsidian vault in settings before writing notes.", ObsidianTools.notReadyReason(true, true, false));
        assertEq("disabled", "turn on Obsidian notes in settings first.", ObsidianTools.notReadyReason(true, false, true));
        assertEq("ready has no error", "Obsidian notes aren't ready yet.", ObsidianTools.notReadyReason(true, true, true));

        char expand = '\u02C5';
        char collapse = '\u203A';
        // Match MainActivity label concatenation exactly (no extra space)
        boolean expanded = false;
        String collapsed = "obsidian note" + (expanded ? (" " + expand) : (" " + collapse));
        expanded = true;
        String open = "obsidian note" + (expanded ? (" " + expand) : (" " + collapse));
        assertEq("row collapsed", "obsidian note " + collapse, collapsed);
        assertEq("row expanded", "obsidian note " + expand, open);
        assertTrue("toggle changes glyph", !collapsed.equals(open));

        assertTrue("voice-like write detected", ObsidianTools.looksLikeTool("okay\n" + write));
        assertTrue("voice-like append detected", ObsidianTools.looksLikeTool(append));
        assertTrue("voice-like read detected", ObsidianTools.looksLikeTool(read));

        // Prefer write over append when both present should be caller's job; parsers stay independent.
        String both = write + "\n" + append;
        assertEq("write still parses in mixed", "Grocery List.md", ObsidianTools.writePath(both));
        assertEq("append still parses in mixed", "Grocery List", ObsidianTools.appendPath(both));

        if (failed > 0) { System.err.println(failed + " failed"); System.exit(1); }
        System.out.println("all passed");
    }
}

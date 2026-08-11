package com.lightos.minimalchat;

import java.io.File;
import java.nio.file.Files;

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

    public static void main(String[] args) throws Exception {
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
        assertTrue("missing app on device phrasing", ObsidianTools.MISSING_APP.toLowerCase().contains("on this device"));
        assertEq("no vault", "link your Obsidian vault in settings before writing notes.", ObsidianTools.notReadyReason(true, true, false));
        assertEq("disabled", "turn on Obsidian notes in settings first.", ObsidianTools.notReadyReason(true, false, true));
        assertEq("ready has no error", "Obsidian notes aren't ready yet.", ObsidianTools.notReadyReason(true, true, true));

        assertEq("row collapsed", "obsidian note ›", ObsidianTools.noteRowLabel(false));
        assertEq("row expanded", "obsidian note ˅", ObsidianTools.noteRowLabel(true));
        assertTrue("toggle changes glyph", !ObsidianTools.noteRowLabel(false).equals(ObsidianTools.noteRowLabel(true)));

        assertTrue("voice-like write detected", ObsidianTools.looksLikeTool("okay\n" + write));
        assertTrue("voice-like append detected", ObsidianTools.looksLikeTool(append));
        assertTrue("voice-like read detected", ObsidianTools.looksLikeTool(read));

        // Prefer write over append when both present should be caller's job; parsers stay independent.
        String both = write + "\n" + append;
        assertEq("write still parses in mixed", "Grocery List.md", ObsidianTools.writePath(both));
        assertEq("append still parses in mixed", "Grocery List", ObsidianTools.appendPath(both));

        // Missing Obsidian → expandable note row with install message (chat + voice share this plan).
        ObsidianTools.ToolOutcome missing = ObsidianTools.planTool(write, false, true, true);
        assertTrue("missing app shows row", missing.showRow);
        assertTrue("missing app notReady", missing.notReady);
        assertEq("missing app row text", ObsidianTools.MISSING_APP, missing.savedText);
        assertEq("missing app answer fallback", ObsidianTools.MISSING_APP, missing.cleanedAnswer);

        ObsidianTools.ToolOutcome noVault = ObsidianTools.planTool(append, true, true, false);
        assertEq("no vault plan", "link your Obsidian vault in settings before writing notes.", noVault.savedText);

        ObsidianTools.ToolOutcome readyPlan = ObsidianTools.planTool(addG, true, true, true);
        assertTrue("ready plan shows row", readyPlan.showRow);
        assertTrue("ready plan is ready", !readyPlan.notReady);
        assertEq("ready plan append path", "Grocery List.md", readyPlan.appendPath);

        ObsidianTools.ToolOutcome plain = ObsidianTools.planTool("hello there", true, true, true);
        assertTrue("plain chat no row", !plain.showRow);

        // File vault: create → append → read (same merge rules as device SAF path).
        File vault = Files.createTempDirectory("lightui-obsidian-vault").toFile();
        try {
            String wrote = ObsidianTools.writeVaultNote(vault, "Grocery List", "# Grocery List\n\n- milk\n- eggs", false);
            assertEq("vault wrote", "wrote Grocery List.md", wrote);
            String body1 = ObsidianTools.readVaultNote(vault, "Grocery List.md");
            assertEq("vault read after write", "# Grocery List\n\n- milk\n- eggs", body1);

            String updated = ObsidianTools.writeVaultNote(vault, "Grocery List.md", "- G", true);
            assertEq("vault updated", "updated Grocery List.md", updated);
            String body2 = ObsidianTools.readVaultNote(vault, "Grocery List.md");
            assertEq("vault after append G", "# Grocery List\n\n- milk\n- eggs\n- G\n", body2);

            String nested = ObsidianTools.writeVaultNote(vault, "Lists/Todo", "- call dentist", false);
            assertEq("vault nested wrote", "wrote Lists/Todo.md", nested);
            assertEq("vault nested read", "- call dentist", ObsidianTools.readVaultNote(vault, "Lists/Todo.md"));

            String createdAppend = ObsidianTools.writeVaultNote(vault, "New Note.md", "- first", true);
            assertEq("vault append creates", "created New Note.md", createdAppend);
            assertEq("vault append create body", "- first\n", ObsidianTools.readVaultNote(vault, "New Note.md"));

            // Voice-shaped model reply: spoken preface + append tool (same path as submitVoiceText → send → callOpenRouter).
            String voiceReply = "Got it, adding G to your grocery list.\n" + addG;
            ObsidianTools.ToolOutcome voicePlan = ObsidianTools.planTool(voiceReply, true, true, true);
            assertTrue("voice plan ready", voicePlan.showRow && !voicePlan.notReady);
            String voiceWrite = ObsidianTools.writeVaultNote(vault, voicePlan.appendPath, ObsidianTools.appendContent(voiceReply), true);
            assertEq("voice append result", "updated Grocery List.md", voiceWrite);
            assertTrue("voice list has G", ObsidianTools.readVaultNote(vault, "Grocery List.md").contains("- G"));
        } finally {
            deleteRec(vault);
        }

        if (failed > 0) { System.err.println(failed + " failed"); System.exit(1); }
        System.out.println("all passed");
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}

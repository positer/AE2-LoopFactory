package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.client.FactoryEditorScreen;
import com.example.ae2lightoptimizer.factory.FactorySyntaxHighlighter;
import com.example.ae2lightoptimizer.mixin.MultiLineEditBoxAccess;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Whence;

/** Native editor fixtures and unchanged framebuffer captures; no OS keyboard simulation. */
public final class SyntaxHighlightAudit {
    public static volatile boolean requested, finished, passed;
    private record Page(String id, String code) {}
    private static final List<Page> PAGES = List.of(
        new Page("indent", "name \"Factory\"\nimport Src, Dst\nfunc work\n    get 64 #c:ingots/iron from Src\n    put P0 into Dst on north\nend\nwork\n# channel comment\nchannel"),
        new Page("logic", "import 输入, 输出\nfunc 搬运\n    if not true and false or P in O do\n        wait 20 tick\n    while 输入 has >= 2 O0 do\n        break\nend\n搬运\ndone"),
        new Page("sfm", "NAME \"SFM\"\nEVERY 20G PLUS 2 TICKS DO\nINPUT MUST 64 iron_ingot EXCEPT gold_ingot\nFROM Src TOP SIDE\nIF OVERALL Dst HAS GE 2 iron_ingot THEN\nOUTPUT fe:: TO Dst BOTTOM SIDE\nELSE\nFORGET Src\nEND"),
        new Page("sfm-conditions", "EVERY REDSTONE PULSE DO\nIF A HAS GT 1 stone AND NOT B HAS LE 2 sand THEN\nINPUT #c:ingots/iron FROM A NORTH SIDE\nELSE IF C HAS EQ 0 dirt OR FALSE THEN\nOUTPUT fluid:minecraft:water TO C NULL SIDE\nEND\n-- INPUT P0 fe:: remain a comment\nEND"),
        new Page("resources", "import A\nget *:ingots/* & !minecraft:gold* from A\nput 123mod:thing into storage on up\nget (P0,O0) from source\nput P into storage\nput O into storage\nwait 2 s\nwait 3 min\ndone"),
        new Page("wrapped-string", "name \"" + "channel get P0 minecraft:stone ".repeat(5) + "\"\ndone"),
        new Page("wrapped-comment", "# " + "channel get P0 minecraft:stone ".repeat(5) + "\ndone"),
        new Page("wrapped-resource", "get minecraft:" + "very_long_resource_".repeat(9) + " from source\ndone"),
        new Page("slash-comments", "name \"https://example/a\" // string remains yellow\nimport 输入, 输出 // labels remain cyan\nchannel// same line comment\n    get 3 #c:ingots/iron from 输入 // resource\n    put P0 into 输出 // reference\n// channel get P0 should stay green\ndone"),
        new Page("edit-before", "// channel"),
        new Page("edit-after", "channel")
    );
    private static final List<Map<String,Object>> rows = new ArrayList<>();
    private static int index, ticks, phase;
    private static MultiLineEditBox widget;

    public static void tick(Minecraft mc) {
        if (!requested || finished) return;
        try {
            if (++ticks > 600) throw new IllegalStateException("Native syntax audit timeout");
            if (!(mc.screen instanceof FactoryEditorScreen)) return;
            if (widget == null) widget = (MultiLineEditBox) read(mc.screen, "editor");
            if (index == PAGES.size()) {
                if (EditorScrollAudit.tick(mc,widget)) {passed=true;finish(Path.of(System.getProperty("ae2lf.probe.reportDir")),"");}
                return;
            }
            var page = PAGES.get(index);
            var root = Path.of(System.getProperty("ae2lf.probe.reportDir"));
            var path = root.resolve("syntax-screenshots/" + page.id + ".png");
            if (phase == 0) {
                Files.createDirectories(path.getParent());
                widget.setValue(page.code);
                widget.setFocused(false);
                ((MultiLineEditBoxAccess) widget).ae2lf$textField().seekCursor(Whence.ABSOLUTE, 0);
                phase = 1; ticks = 0; return;
            }
            if (phase == 1 && ticks >= 12) {
                if (!widget.getValue().equals(page.code)) throw new IllegalStateException("Editor fixture changed");
                if (!page.code.equals(read(widget,"ae2lf$highlightSource"))) throw new IllegalStateException("Render cache did not refresh");
                if (!FactorySyntaxHighlighter.highlight(page.code).equals(read(widget,"ae2lf$highlightSpans")))
                    throw new IllegalStateException("Native mixin spans differ");
                NativeIdInput.capture(mc,path);
                phase = 2; ticks = 0; return;
            }
            if (phase == 2 && ticks >= 4 && Files.isRegularFile(path)) {
                var field = ((MultiLineEditBoxAccess)widget).ae2lf$textField();
                var row = new LinkedHashMap<String,Object>();
                row.put("id",page.id); row.put("source",page.code); row.put("passed",true);
                row.put("screenshot",root.relativize(path).toString());
                row.put("editor",List.of(widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight()));
                row.put("guiScale",mc.getWindow().getGuiScale());
                row.put("visualLines",field.getLineCount());
                row.put("cachedSpans",FactorySyntaxHighlighter.highlight(page.code));
                rows.add(row);
                if (++index == PAGES.size()) { phase = 0; ticks = 0; }
                else { phase = 0; ticks = 0; }
            }
        } catch (Throwable failure) {
            finish(Path.of(System.getProperty("ae2lf.probe.reportDir")),failure.toString());
        }
    }
    private static Object read(Object target,String name) {
        try { var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target); }
        catch(ReflectiveOperationException failure) {throw new IllegalStateException(name,failure);}
    }
    private static void finish(Path root,String failure) {
        try { Files.writeString(root.resolve("syntax-highlight-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create()
            .toJson(Map.of("status",passed?"passed":"failed","failure",failure,"pages",rows,"inputMethod","native widget setValue, native render, framebuffer capture"))); }
        catch(Exception error) {throw new IllegalStateException(error);}
        finished=true;
    }
    private SyntaxHighlightAudit() {}
}

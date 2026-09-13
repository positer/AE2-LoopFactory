package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.mixin.MultiLineEditBoxAccess;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Whence;

/** Native cursor, wheel, click and selection coordinates paired with actual framebuffer rows. */
final class EditorScrollAudit {
    private static final String LONG = java.util.stream.IntStream.range(0,40)
            .mapToObj(i -> "// row_" + String.format(java.util.Locale.ROOT,"%02d",i) + " visible code")
            .collect(java.util.stream.Collectors.joining("\n"));
    private static final String WRAPPED = "// " + "wrapped resource channel ".repeat(120);
    private static final List<String> CASES=List.of("top","cursor-down","cursor-end","cursor-up",
            "wheel-fractional","click-after-wheel","selection","wrapped-end","shrink","empty");
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static int index,phase,ticks;
    private static double clickY;
    static boolean tick(Minecraft mc,MultiLineEditBox widget) throws Exception {
        var field=((MultiLineEditBoxAccess)widget).ae2lf$textField();
        var root=Path.of(System.getProperty("ae2lf.probe.reportDir"));
        String id=CASES.get(index);
        Path picture=root.resolve("scroll-screenshots/"+id+".png");
        if(phase==0) {
            Files.createDirectories(picture.getParent());
            widget.setFocused(true);
            switch(id) {
                case "top" -> {widget.setValue(LONG);field.seekCursor(Whence.ABSOLUTE,0);}
                case "cursor-down" -> {for(int i=0;i<15;i++)field.seekCursorLine(1);}
                case "cursor-end" -> field.seekCursor(Whence.ABSOLUTE,LONG.length());
                case "cursor-up" -> {for(int i=0;i<12;i++)field.seekCursorLine(-1);}
                case "wheel-fractional" -> {field.seekCursor(Whence.ABSOLUTE,0);widget.mouseScrolled(widget.getX()+20,widget.getY()+20,0,-4.5);}
                case "click-after-wheel" -> {
                    clickY=widget.getY()+4+4*9+3;
                    int expected=(int)((clickY-widget.getY()-4+scroll(widget))/9);
                    NativeIdInput.click(mc,widget.getX()+4,clickY,0);
                    if(field.getLineAtCursor()!=expected)throw new IllegalStateException("Click row differs from native visible row");
                }
                case "selection" -> {
                    field.seekCursor(Whence.ABSOLUTE,LONG.indexOf("// row_20"));field.setSelecting(true);
                    field.seekCursorLine(6);field.setSelecting(false);
                }
                case "wrapped-end" -> {widget.setValue(WRAPPED);field.seekCursor(Whence.ABSOLUTE,WRAPPED.length());}
                case "shrink" -> {widget.setValue("// short\ndone");field.seekCursor(Whence.ABSOLUTE,0);}
                case "empty" -> widget.setValue("");
                default -> throw new IllegalStateException(id);
            }
            phase=1;ticks=0;return false;
        }
        if(++ticks<12)return false;
        if(phase==1) {NativeIdInput.capture(mc,picture);phase=2;ticks=0;return false;}
        if(!Files.isRegularFile(picture))return false;
        double scroll=scroll(widget);
        var visible=new ArrayList<Map<String,Object>>();
        for(int i=0;i<field.getLineCount();i++) {
            double y=widget.getY()+4+i*9-scroll;
            var line=field.getLineView(i);
            if(y>=widget.getY()+1 && y+9<=widget.getY()+widget.getHeight()-1)
                visible.add(Map.of("line",i,"y",y,"text",widget.getValue().substring(lineIndex(line,"beginIndex"),lineIndex(line,"endIndex"))));
        }
        var row=new LinkedHashMap<String,Object>();
        row.put("id",id);row.put("scroll",scroll);row.put("cursor",field.cursor());row.put("cursorLine",field.getLineAtCursor());
        row.put("cursorX",widget.getX()+4+mc.font.width(widget.getValue().substring(lineIndex(field.getLineView(field.getLineAtCursor()),"beginIndex"),field.cursor())));
        row.put("cursorY",widget.getY()+4+field.getLineAtCursor()*9-scroll);
        row.put("editor",List.of(widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight()));
        row.put("scale",mc.getWindow().getGuiScale());row.put("visibleLines",visible);row.put("screenshot",root.relativize(picture).toString());
        row.put("source",widget.getValue());row.put("clickY",clickY);rows.add(row);
        phase=0;ticks=0;
        if(++index<CASES.size())return false;
        Files.writeString(root.resolve("editor-scroll-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("status","captured","cases",rows,"inputMethod","native field cursor movement, widget wheel callback and native mouse callbacks")));
        return true;
    }
    private static double scroll(MultiLineEditBox widget) throws Exception {
        Class<?> type=widget.getClass();
        while(type!=null) {
            try {var method=type.getDeclaredMethod("scrollAmount");method.setAccessible(true);return ((Number)method.invoke(widget)).doubleValue();}
            catch(NoSuchMethodException ignored){type=type.getSuperclass();}
        }
        throw new IllegalStateException("Native scroll amount unavailable");
    }
    private static int lineIndex(Object line,String name) throws Exception {
        var method=line.getClass().getDeclaredMethod(name);method.setAccessible(true);return ((Number)method.invoke(line)).intValue();
    }
    private EditorScrollAudit() {}
}

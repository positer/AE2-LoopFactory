package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FactoryGuideExamplesTest {
    @Test void everyPublishedExampleCompilesInItsDeclaredRecipeMode() throws Exception {
        var root=Path.of("../..");
        var examples=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("tools/runtime-factory-probe/examples/catalog.json"))).getAsJsonArray();
        assertEquals(8,examples.size());
        for(var element:examples) {
            var example=element.getAsJsonObject();var code=example.get("code").getAsString();
            assertDoesNotThrow(()->FactoryCompiler.compile(code,example.get("recipe").getAsBoolean(),example.get("inputs").getAsInt(),example.get("outputs").getAsInt()),example.get("id").getAsString());
            for(var gen:new String[]{"1.21.1","26.1.2"})for(var locale:new String[]{"","_zh_cn/"}) {
                var guide=root.resolve("versions/neoforge-"+gen+"/src/main/resources/assets/ae2lightoptimizer/ae2guide/"+locale+"items-blocks-machines/loop_factory.md");
                assertTrue(Files.readString(guide).replace("\r\n","\n").contains("```text\n"+code+"\n```"),guide+" missing exact example "+example.get("id"));
                assertFalse(Files.readString(guide).contains("<!--"),"GuideME uses MDX comments, not HTML comments");
            }
            if(example.get("recipe").getAsBoolean())assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile(code,false,0,0));
        }
    }
}

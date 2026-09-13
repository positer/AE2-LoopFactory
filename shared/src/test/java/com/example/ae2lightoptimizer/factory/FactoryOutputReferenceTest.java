package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryOutputReferenceTest {
    @Test void outputReferencesAreDistinctFromInputReferences() {
        assertTrue(FactorySelector.parse("O2").matches("minecraft::item","minecraft:gold_ingot",index->index==-2));
        assertFalse(FactorySelector.parse("P2").matches("minecraft::item","minecraft:gold_ingot",index->index==-2));
        assertEquals(java.util.Set.of(-1,-2),FactorySelector.parse("O1!(O2)").parameters());
    }
    @Test void outputsWorkInTransfersConditionsExclusionsAndSfm() {
        assertDoesNotThrow(()->FactoryCompiler.compile("import A\nget must 2 O1 from A\nif A has O2 > 0 do\n    put O1!(O2) into source\ndone",true,1,2));
        assertDoesNotThrow(()->FactoryCompiler.compile("EVERY TICK DO INPUT MUST 2 O1 FROM A OUTPUT MUST 2 O1 TO source END",true,1,2));
    }
    @Test void noRecipeAndOutOfRangeAreRejected() {
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("put O1 into storage",false,0,0));
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("put O3 into source",true,8,2));
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("if source has O2 do\n    done",true,8,1));
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("EVERY TICK DO OUTPUT O3 TO source END",true,8,2));
    }
    @Test void outputNamesAreReservedAndIndexesArePositive() {
        assertFalse(FactoryTags.validName("O1"));
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("EVERY TICK DO INPUT O1 FROM O2 END",true,1,2));
        for(String code:new String[]{"import O1","func O2\n    done\nend","put O0 into source","put O01 into source","put On into source"})
            assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile(code,true,1,2));
    }
}

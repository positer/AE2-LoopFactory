package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryParserBoundaryTest {
    @Test void recipeRejectsEquivalentConstantTrueLoops() {
        for(String condition:new String[]{"(true)","1 > 0","-1","not false","false or true","true or A has minecraft:iron_ingot"}) {
            String code="import A\nwhile "+condition+" do\n    wait 1 tick";
            assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile(code,true),condition);
        }
    }
    @Test void explicitFaceClauseRequiresADirection() {
        for(String instruction:new String[]{"get minecraft:iron_ingot from A on","put minecraft:iron_ingot into A on","get minecraft:iron_ingot from A on   "})
            assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("import A\n"+instruction,false),instruction);
    }
    @Test void destinationCannotSmuggleExtraTokensAsPartOfATag() {
        for(String destination:new String[]{"A extra","A on north extra","A on on north"})
            assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("import A\nput minecraft:iron_ingot into "+destination,false));
    }
    @Test void dynamicRecipeLoopsAndStandaloneYieldingLoopsRemainLegal() {
        assertDoesNotThrow(()->FactoryCompiler.compile("import A\nwhile A has minecraft:iron_ingot > 0 do\n    wait 1 tick",true));
        assertDoesNotThrow(()->FactoryCompiler.compile("while (true) do\n    wait 1 tick",false));
        assertDoesNotThrow(()->FactoryCompiler.compile("while false do\n    wait 1 tick",true));
        assertDoesNotThrow(()->FactoryCompiler.compile("import A\nget minecraft:iron_ingot from A\nput minecraft:iron_ingot into A on north",false));
    }
}

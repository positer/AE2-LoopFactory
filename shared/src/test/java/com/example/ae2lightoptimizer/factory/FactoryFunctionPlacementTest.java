package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryFunctionPlacementTest {
    private static final class Host implements FactoryMachine.Host {
        int outputs;
        public long count(String tag,String resource){return 1;}
        public long transfer(boolean get,String resource,String tag,String face,long limit){if(!get)outputs++;return get?0:1;}
        public void pulse(String tag,long ticks){}
    }
    private static void executes(String source,int outputs) {
        var program=FactoryCompiler.compile(source,false);var vm=new FactoryMachine(program);var host=new Host();
        for(int tick=0;tick<20&&!vm.stopped();tick++){vm.tick(host);vm=FactoryMachine.restore(program,vm.snapshot());}
        assertTrue(vm.stopped());assertEquals("",vm.error());assertEquals(outputs,host.outputs);
    }
    @Test void callAndImportsCanPrecedeOrFollowDeclaration() {
        executes("work\ndone\nfunc work\n    get minecraft:stone from A\n    wait 1 tick\n    put minecraft:stone into storage\nend\nimport A",1);
    }
    @Test void definitionInUnexecutedBranchIsStillAvailable() {
        executes("work\nif false do\n    func work\n        put minecraft:stone into storage\n    end\ndone",1);
    }
    @Test void nestedDefinitionsAreProgramScopedAndDoNotRunOnDeclaration() {
        executes("outer\ndone\nfunc outer\n    inner\n    func inner\n        wait 1 tick\n        put minecraft:stone into storage\n    end\nend",1);
    }
    @Test void declarationsInLoopsRemainDefinitions() {
        executes("inner\nwhile false do\n    func inner\n        put minecraft:stone into storage\n    end\ndone",1);
    }
    @Test void collisionsAndMissingEndRemainErrorsRegardlessOfOrder() {
        for(String source:new String[]{"func A\n    wait 1 tick\nend\nimport A","import A\nfunc A\n    wait 1 tick\nend",
                "if false do\n    func work\n        wait 1 tick","func wait\n    done\nend",
                "func work\n    done\nend\nif false do\n    func work\n        done\n    end"})
            assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile(source,false));
    }
}

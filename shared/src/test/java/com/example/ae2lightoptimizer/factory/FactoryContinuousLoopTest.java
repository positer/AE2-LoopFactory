package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryContinuousLoopTest {
    private static final class Host implements FactoryMachine.Host {
        int operations, pulses;
        public long count(String tag,String resource){return 0;}
        public long transfer(boolean get,String resource,String tag,String face,long limit){operations++;return get?0:1;}
        public void pulse(String tag,long ticks){pulses++;}
    }
    @Test void recipeFreeProgramHasNoLifetimeLimitAndRestores() {
        var program=FactoryCompiler.compile("while true do\n    put minecraft:stone into storage\n    wait 1 tick",false);
        var vm=FactoryMachine.restore(program,new FactoryMachine.Snapshot(0,0,java.util.List.of(),false,"",1_000_000));
        var host=new Host();
        for(int tick=0;tick<100_000;tick++){vm.tick(host);vm=FactoryMachine.restore(program,vm.snapshot());}
        assertEquals(100_000,host.operations);assertFalse(vm.stopped());assertEquals("",vm.error());
    }
    @Test void calledWaitAndRedstoneYieldButOtherInstructionsRunInSameTick() {
        for(String pause:new String[]{"wait 1 tick","redstone A 1 tick"}) {
            var p=FactoryCompiler.compile("import A\nwhile true do\n    put minecraft:stone into storage\n    rest\nfunc rest\n    put minecraft:stone into storage\n    "+pause+"\nend",false);
            var vm=new FactoryMachine(p);var host=new Host();
            vm.tick(host);assertEquals(2,host.operations);
            vm.tick(host);assertEquals(4,host.operations);assertEquals("",vm.error());
        }
    }
    @Test void unexecutedWaitDoesNotProtectAZeroTickLoop() {
        for(boolean recipe:new boolean[]{false}) {
            var vm=new FactoryMachine(FactoryCompiler.compile("while true do\n    if false do\n        wait 1 tick",recipe));
            vm.tick(new Host());assertTrue(vm.stopped());assertTrue(vm.error().contains("no progress"));
        }
    }
    @Test void recipeRejectsUnconditionalLoopEvenWhenItYields() {
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("while true do\n    wait 1 tick",true));
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("import A\nwhile true do\n    redstone A 1 tick",true));
    }
    @Test void innerZeroTickLoopIsNotProtectedByOuterWait() {
        var vm=new FactoryMachine(FactoryCompiler.compile("while true do\n    wait 1 tick\n    while true do\n        if false do\n            wait 1 tick",false));
        vm.tick(new Host());assertFalse(vm.stopped());
        vm.tick(new Host());assertTrue(vm.stopped());assertTrue(vm.error().contains("no progress"));
    }
    @Test void ordinarySuccessfulTransfersShareOneTick() {
        var vm=new FactoryMachine(FactoryCompiler.compile("put minecraft:stone into storage\nput minecraft:iron_ingot into storage\ndone",false));
        var host=new Host();vm.tick(host);
        assertEquals(2,host.operations);assertTrue(vm.stopped());assertEquals("",vm.error());
        assertEquals(0,vm.snapshot().delay());
    }
}

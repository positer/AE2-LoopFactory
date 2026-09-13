package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryMustTest {
    private static final class Host implements FactoryMachine.Host {
        long available, moved, required;
        public long count(String tag,String resource){return available;}
        public void pulse(String tag,long ticks){}
        public void declare(String resource,String tag,String face,long limit,boolean must){if(must)required=limit;}
        public long requiredTransfer(String resource,String tag,String face,long limit){return Math.min(required,limit);}
        public long transfer(boolean get,String resource,String tag,String face,long limit){
            if(get)return 0;
            long sent=Math.min(available,limit);available-=sent;moved+=sent;required=Math.max(0,required-sent);return sent;
        }
    }
    @Test void ordinaryZeroTransferSkipsAndRunsFollowingInstructionInSameTick() {
        var p=FactoryCompiler.compile("put 64 minecraft:iron_ingot into storage\nwait 5 tick\ndone",false);
        var vm=new FactoryMachine(p);var h=new Host();vm.tick(h);
        assertEquals(2,vm.snapshot().pc());assertEquals(5,vm.snapshot().delay());
        assertFalse(vm.snapshot().pendingTransfer());assertEquals(0,h.moved);
    }
    @Test void ordinaryQuantityAcceptsPartialWithoutWaitingForRemainder() {
        var p=FactoryCompiler.compile("put 64 minecraft:iron_ingot into storage\ndone",false);
        var vm=new FactoryMachine(p);var h=new Host();h.available=3;vm.tick(h);
        assertTrue(vm.stopped());assertEquals(3,h.moved);
    }
    @Test void ordinaryZeroTransferDoesNotImplicitlyYieldAnEndlessLoop() {
        var p=FactoryCompiler.compile("while true do\n    put 1 minecraft:iron_ingot into storage\n    if false do\n        wait 1 tick",false);
        var vm=new FactoryMachine(p);vm.tick(new Host());
        assertTrue(vm.stopped());assertTrue(vm.error().contains("no progress"));
    }
    @Test void mustAccumulatesWithoutRepeatingAfterRestore() {
        var p=FactoryCompiler.compile("put must 64 minecraft:iron_ingot into storage\ndone",false);
        var vm=new FactoryMachine(p);var h=new Host();h.available=20;vm.tick(h);
        assertEquals(44,vm.snapshot().transferRemaining());assertEquals(0,vm.snapshot().pc());
        vm=FactoryMachine.restore(p,vm.snapshot());vm.tick(h);assertEquals(20,h.moved);
        h.available=60;vm.tick(h);assertTrue(vm.stopped());assertEquals(64,h.moved);assertEquals(16,h.available);
    }
    @Test void mustSourcePassesItsRequirementToPut() {
        var p=FactoryCompiler.compile("import A\nget must 64 minecraft:iron_ingot from A\nput minecraft:iron_ingot into storage\ndone",false);
        var vm=new FactoryMachine(p);var h=new Host();h.available=20;vm.tick(h);
        assertFalse(vm.stopped());assertEquals(44,vm.snapshot().transferRemaining());
        h.available=44;vm.tick(h);assertTrue(vm.stopped());assertEquals(64,h.moved);
    }
    @Test void waitingTaskDoesNotBlockAnotherTask() {
        var p=FactoryCompiler.compile("put must 4 minecraft:iron_ingot into storage\ndone",false);
        var first=new FactoryMachine(p);var second=new FactoryMachine(p);var a=new Host();var b=new Host();
        b.available=4;first.tick(a);second.tick(b);
        assertFalse(first.stopped());assertTrue(second.stopped());assertEquals(4,b.moved);
        a.available=4;first.tick(a);assertTrue(first.stopped());assertEquals(4,a.moved);
    }
    @Test void mustRequiresPositiveQuantity() {
        for(String code:new String[]{"put must minecraft:stone into storage","put must 0 minecraft:stone into storage","put must -1 minecraft:stone into storage"})
            assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile(code,false));
    }
}

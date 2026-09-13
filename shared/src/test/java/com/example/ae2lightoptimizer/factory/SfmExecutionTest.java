package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SfmExecutionTest {
    private static final class Host implements FactoryMachine.Host {
        final FactoryRoutes routes=new FactoryRoutes();long time,moved;boolean signal,complete;
        public long count(String tag,String resource){return 1000-moved;}
        public long transfer(boolean get,String resource,String tag,String face,long limit) {
            if(get){routes.declare(resource,tag,face,limit);return 0;}
            return routes.output(resource,tag,face,limit,(s,r,t,f,n)->{moved+=n;return n;});
        }
        public void forget(String tag){routes.forget(tag);}
        public void pulse(String tag,long ticks){}
        public long gameTime(){return time;}
        public boolean signal(){return signal;}
        public boolean recipeComplete(){return complete;}
    }
    @Test void timerRunsOnlyWhenDueAndSurvivesEveryTickRestore() {
        var program=FactoryCompiler.compile("NAME \"Smelter\"\nEVERY 2 TICKS DO INPUT 1 iron_ingot FROM A OUTPUT TO B END",false);
        var machine=new FactoryMachine(program);var host=new Host();
        for(int i=1;i<=1000;i++){host.time=i;machine.tick(host);machine=FactoryMachine.restore(program,machine.snapshot());}
        assertEquals(500,host.moved);assertEquals("",machine.error());assertFalse(machine.stopped());
        assertTrue(host.routes.snapshot().isEmpty());assertEquals("Smelter",program.name());
    }
    @Test void independentGlobalTimerAndRedstoneEdgeDoNotRepeatOnHeldSignal() {
        var program=FactoryCompiler.compile("""
                EVERY 4 GLOBAL PLUS 1 TICKS DO
                    IF A HAS GE 1 iron_ingot THEN
                        INPUT 1 iron_ingot FROM A
                        OUTPUT TO B
                    END
                END
                EVERY REDSTONE PULSE DO
                    INPUT 2 iron_ingot FROM A
                    OUTPUT TO B
                END
                """,false);
        var machine=new FactoryMachine(program);var host=new Host();
        for(int i=1;i<=12;i++){host.time=i;host.signal=i>=2&&i<=10;machine.tick(host);machine=FactoryMachine.restore(program,machine.snapshot());}
        assertEquals(5,host.moved);assertEquals("",machine.error());
    }
    @Test void recipeSchedulerStopsAfterItsOutputsHaveReturned() {
        var program=FactoryCompiler.compile("EVERY TICK DO INPUT 1 P1 FROM source OUTPUT TO B END",true,1);
        var machine=new FactoryMachine(program);var host=new Host();machine.tick(host);
        assertEquals(1,host.moved);host.complete=true;machine.tick(host);assertTrue(machine.stopped());assertEquals(1,host.moved);
    }
    @Test void unsupportedClausesFailRatherThanSilentlyChangingTheirMeaning() {
        for(String clause:new String[]{"INPUT RETAIN 4 iron_ingot FROM A","INPUT FROM EACH A","OUTPUT TO A SLOTS 1-2"})
            assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("EVERY TICK DO "+clause+" END",false));
    }
    @Test void quotedLabelsExclusionsAndBooleanConditionsSurviveCompilation() {
        var program=FactoryCompiler.compile("EVERY TICK DO IF NOT FALSE AND (\"input chest\" HAS GE 1 iron_ingot OR FALSE) THEN INPUT 1 * EXCEPT gold_ingot, stone FROM \"input chest\" OUTPUT TO B END END",false);
        assertTrue(program.tags().contains("input chest"));
        assertTrue(program.instructions().stream().anyMatch(i->i.op()==FactoryProgram.Op.GET && i.argument().contains("minecraft::item!(minecraft:gold_ingot,minecraft:stone)")));
        var tags=new FactoryTags();tags.reconcile(program.tags());assertEquals(tags.snapshot(),FactoryTags.restore(tags.snapshot()).snapshot());
        var host=new Host();var machine=new FactoryMachine(program);machine.tick(host);assertEquals(1,host.moved);assertEquals("",machine.error());
    }
    @Test void unreachableBranchIsStillValidatedButRuntimeShortCircuits() {
        assertThrows(FactoryProgram.CompileException.class,()->FactoryCompiler.compile("if false and Missing has minecraft:stone do\n    done",false));
        assertEquals(0,FactoryExpression.evaluate("false and A has minecraft:stone",(a,b)->{throw new AssertionError();}));
        assertEquals(1,FactoryExpression.evaluate("true or A has minecraft:stone",(a,b)->{throw new AssertionError();}));
    }
}

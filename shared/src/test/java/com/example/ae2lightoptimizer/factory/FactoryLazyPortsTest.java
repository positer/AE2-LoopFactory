package com.example.ae2lightoptimizer.factory;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FactoryLazyPortsTest {
    @Test void successfulUnsidedAccessQueriesOneOfSevenCandidates() {
        var calls=new ArrayList<Integer>();
        var ports=new FactoryLazyPorts<Integer>(7,i->{calls.add(i);return i;});
        assertEquals(0,ports.first());
        for(int port:ports) { assertEquals(0,port); break; }
        assertEquals(List.of(0),calls);
    }
    @Test void fallbackIsOrderedAndReusedWithinAnOperation() {
        var calls=new ArrayList<Integer>();
        var ports=new FactoryLazyPorts<Integer>(7,i->{calls.add(i);return i==0||i==1?null:i;});
        assertEquals(2,ports.first());
        var accepted=new ArrayList<Integer>();
        for(int port:ports){accepted.add(port);if(port==4)break;}
        assertEquals(List.of(2,3,4),accepted);
        assertEquals(List.of(0,1,2,3,4),calls);
        for(int port:ports){if(port==4)break;}
        assertEquals(5,calls.size());
    }
    @Test void completeRefusalCanInspectAllFacesWithoutDuplicateQueries() {
        var calls=new ArrayList<Integer>();
        var ports=new FactoryLazyPorts<Integer>(7,i->{calls.add(i);return i;});
        for(int ignored:ports){}
        for(int ignored:ports){}
        assertEquals(7,calls.size());
    }
    @Test void missingCapabilitiesAndFreshOperationsAreNotCachedAcrossTime() {
        int[] generation={0};
        var absent=new FactoryLazyPorts<Integer>(7,i->null);
        assertNull(absent.first());assertFalse(absent.iterator().hasNext());
        var first=new FactoryLazyPorts<Integer>(7,i->generation[0]);
        assertEquals(0,first.first());generation[0]=1;
        var next=new FactoryLazyPorts<Integer>(7,i->generation[0]);
        assertEquals(1,next.first());
    }
}

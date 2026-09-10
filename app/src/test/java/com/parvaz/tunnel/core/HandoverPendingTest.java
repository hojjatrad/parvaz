package com.parvaz.tunnel.core;
import org.junit.Test;import static org.junit.Assert.*;
public class HandoverPendingTest {
 @Test public void newestRequestWinsAndQueueRemainsSingle(){HandoverPending p=new HandoverPending();Runnable last=()->{};p.offer(1,2,false,()->{});p.offer(1,2,false,last);assertSame(last,p.take().reconnect);assertNull(p.take());}
 @Test public void resolverResetIsStickyWithinSameSession(){HandoverPending p=new HandoverPending();p.offer(1,2,true,()->{});p.offer(1,2,false,()->{});assertTrue(p.take().resetDns);}
 @Test public void newSessionDoesNotInheritOldCacheReset(){HandoverPending p=new HandoverPending();p.offer(1,2,true,()->{});p.offer(3,4,false,()->{});HandoverPending.Work w=p.take();assertFalse(w.resetDns);assertTrue(w.owns(3,4));assertFalse(w.owns(1,2));}
 @Test public void stoppedOrReplacedOperationCannotRun(){HandoverPending p=new HandoverPending();p.offer(7,10,true,()->{});HandoverPending.Work w=p.take();assertFalse(w.owns(8,10));assertFalse(w.owns(7,11));}
 @Test public void clearDropsPendingWork(){HandoverPending p=new HandoverPending();p.offer(1,2,true,()->{});p.clear();assertNull(p.take());}
}

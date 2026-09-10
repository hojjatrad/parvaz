package com.parvaz.tunnel.core;
import java.util.concurrent.atomic.AtomicLong;
/** In-memory revision, not a network address or identifier. A -> B -> A is still
 * a new observation scope. No resolver contents are logged or persisted. */
final class NetworkEpoch {
 private static final AtomicLong revision=new AtomicLong(),resolver=new AtomicLong();
 static long resolver(){return resolver.get();}
 static void resolverChanged(){resolver.incrementAndGet();changed();}
 static long current(){return revision.get();}
 static boolean owns(long observed){return observed==revision.get();}
 static void changed(){revision.incrementAndGet();}
}

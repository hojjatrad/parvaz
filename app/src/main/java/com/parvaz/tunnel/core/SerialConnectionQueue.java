package com.parvaz.tunnel.core;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/** One lifecycle worker, at most one pending replacement, and an independent
 * revision for invalidation while native work is running. Native interruption is
 * best effort; cleanup and the next start never execute concurrently. */
final class SerialConnectionQueue implements AutoCloseable {
 interface Work {void run(long ticket);}
 private final ReentrantLock execution=new ReentrantLock();
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"parvaz-connection");t.setDaemon(true);return t;});
 private volatile long revision;private volatile boolean wanted,closed;private Thread active;
 SerialConnectionQueue(){worker.allowCoreThreadTimeOut(true);}
 long ticket(){return revision;}
 boolean current(long ticket){return !closed&&wanted&&ticket==revision;}
 synchronized boolean start(boolean replace,Work work){
  if(closed||(!replace&&wanted))return false;
  wanted=true;enqueue(work);return true;
 }
 synchronized boolean replace(Work work){return replace(revision,work);}
 synchronized boolean replace(long owner,Work work){if(!current(owner))return false;enqueue(work);return true;}
 private void enqueue(Work work){
  long ticket=++revision;worker.getQueue().clear();interruptActive();
  worker.execute(()->{
   execution.lock();
   try{
    synchronized(this){if(!current(ticket))return;active=Thread.currentThread();}
    work.run(ticket);
   }finally{synchronized(this){if(active==Thread.currentThread())active=null;}execution.unlock();}
  });
 }
 /** Only short, nonblocking state updates here. Never wait for cleanup in a commit. */
 synchronized boolean commit(long ticket,Runnable update){if(!current(ticket))return false;update.run();return true;}
 void stop(Runnable cleanup){stopIfCurrent(-1,cleanup);}
 boolean stopIfCurrent(long owner,Runnable cleanup){
  long stopped;
  synchronized(this){if(owner>=0&&!current(owner))return false;wanted=false;stopped=++revision;worker.getQueue().clear();interruptActive();}
  execution.lock();
  try{synchronized(this){if(revision==stopped&&!wanted)cleanup.run();}}
  finally{execution.unlock();}
  return true;
 }
 private void interruptActive(){if(active!=null&&active!=Thread.currentThread())active.interrupt();}
 @Override public synchronized void close(){closed=true;wanted=false;revision++;worker.getQueue().clear();interruptActive();worker.shutdownNow();}
 int pending(){return worker.getQueue().size();}
}

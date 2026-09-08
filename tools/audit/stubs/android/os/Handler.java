package android.os;
/** Immediate test callback, NOT Android main-thread behavior. */
public class Handler { public Handler(Looper l){} public boolean post(Runnable r){r.run();return true;} }

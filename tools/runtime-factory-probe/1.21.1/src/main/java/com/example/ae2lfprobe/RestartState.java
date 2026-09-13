package com.example.ae2lfprobe;
final class RestartState {
    static volatile boolean exitRequested;
    static volatile boolean visualReady;
    static final boolean PREPARE = "prepare".equals(System.getProperty("ae2lf.probe.restart"));
    static final boolean RESUME = "resume".equals(System.getProperty("ae2lf.probe.restart"));
}

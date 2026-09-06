package com.example.ae2loprobe;

/** Test-mod-only bridge to the native client screenshot/bootstrap helper. */
public final class ProbeState {
    public static volatile String screenshotRequest;
    public static volatile String screenshotCompleted;
    public static volatile String phase = "idle";
    public static volatile String status = "";
    public static volatile boolean finished;
    private ProbeState() { }
}

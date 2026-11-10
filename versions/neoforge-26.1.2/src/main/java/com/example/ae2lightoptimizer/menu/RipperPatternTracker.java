package com.example.ae2lightoptimizer.menu;

/** Native pattern access terminals must recreate their fixed-size mirror when a Ripper grows/shrinks. */
public interface RipperPatternTracker {
    boolean ae2lf$patternSizeChanged();
    long ae2lf$inventoryId();
}

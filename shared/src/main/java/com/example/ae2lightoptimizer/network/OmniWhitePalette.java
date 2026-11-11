package com.example.ae2lightoptimizer.network;

/** Identical for ARGB and ABGR: white replacement is symmetric in all three color components. */
public final class OmniWhitePalette {
    public static int neutralize(int pixel) {
        if ((pixel >>> 24) == 0) return pixel;
        int a = pixel & 255, b = (pixel >>> 8) & 255, c = (pixel >>> 16) & 255;
        int brightest = Math.max(a, Math.max(b, c));
        return (pixel & 0xFF000000) | (brightest << 16) | (brightest << 8) | brightest;
    }
    private OmniWhitePalette() {}
}

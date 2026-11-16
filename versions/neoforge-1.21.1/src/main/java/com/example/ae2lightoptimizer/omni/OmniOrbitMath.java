package com.example.ae2lightoptimizer.omni;

import net.minecraft.core.Direction;

/** Orthonormal basis for the ring plane perpendicular to a connector's base normal. */
public final class OmniOrbitMath {
    public record Vec(double x, double y, double z) {}
    public record Ring(Vec u, Vec v) {}
    private OmniOrbitMath() {}
    public static Ring basis(Direction axis) {
        double ax = axis.getStepX(), ay = axis.getStepY(), az = axis.getStepZ();
        double hx = Math.abs(ay) > 0.5 ? 1 : 0;
        double hy = Math.abs(ay) > 0.5 ? 0 : 1;
        double hz = 0;
        double dot = hx * ax + hy * ay + hz * az;
        double ux = hx - ax * dot, uy = hy - ay * dot, uz = hz - az * dot;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        double vx = ay * uz - az * uy, vy = az * ux - ax * uz, vz = ax * uy - ay * ux;
        return new Ring(new Vec(ux, uy, uz), new Vec(vx, vy, vz));
    }
    /** Unit tangent of the ring at a radial offset, around the given axis. */
    public static Vec tangent(Direction axis, double rx, double ry, double rz) {
        double ax = axis.getStepX(), ay = axis.getStepY(), az = axis.getStepZ();
        double tx = ay * rz - az * ry, ty = az * rx - ax * rz, tz = ax * ry - ay * rx;
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        return len < 1e-6 ? new Vec(0, 0, 0) : new Vec(tx / len, ty / len, tz / len);
    }
}

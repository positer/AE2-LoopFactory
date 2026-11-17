package com.example.ae2lightoptimizer.mixin;

import appeng.api.networking.IManagedGridNode;

/** Exposes the condenser's managed node to the Loop Card driver. */
public interface CondenserLoopNode {
    IManagedGridNode ae2lf$node();
}

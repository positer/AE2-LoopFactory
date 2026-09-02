# 2026-09-02 Loop Storage Cell FE "stuck at tens of bytes" root cause

## Evidence

Instrumented runs in `ImmortalStorage-1.21.1` showed the mounted Loop Storage Cell was never rebuilt and its FE amount oscillated by exactly one 3,276,800-FE batch:

`68,812,800 -> 72,089,600 -> 68,812,800 -> ...`

A throttled stack trace on `extract()` identified the drainer:

```
EnergyDistributeService.onLevelEndTick
  -> TileFluxAccessor.distribute
  -> EnergyHandler.send
  -> MekEnergyCap.send
  -> NetworkStorage.extract
  -> delegate/extract on the Loop Storage Cell
```

## Conclusion

The Applied Flux Flux Accessor is bidirectional: external energy is pushed into the network through its exposed energy capability, but every tick `distribute()` also discharges stored network FE back into adjacent devices. With the Mekanism creative/energy source on a connected side, each imported batch is immediately returned to the source. The Loop Storage Cell is simply the network FE storage being cycled; it is not rejecting inserts and its capacity/accounting is not the limiter.

## Correct action

Use an import-only ingress for this topology: enable Applied Flux's `misc.enable` and use an AE2 ME Import Bus with the FE key filter, or separate the one-way FE path from the source that can also receive. Applied Flux 1.21.1 has no import-only mode for the Flux Accessor.

## Code status

The temporary `ae2lo-*` probes were removed. The eager `persist()` improvement and optional Applied Flux cache-invalidation mixins remain (they are harmless and keep the network FE view fresh); neither masks the external discharge loop.

Clean builds: 1.21.1 `C83D27F6BAE54523A27994D43408894675F3267ABC07E698B45DEC6DB2C520E9`; 26.1.2 `CC5DA80E0C21D457AEED6DED448D9FF78DEAB5F3E2178224352A697A3A9B4AD4`.

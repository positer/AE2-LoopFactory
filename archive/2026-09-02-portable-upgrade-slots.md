# 2026-09-02 Portable Loop Storage Cell upgrade slots

## Request

Portable Loop Storage Cell upgrade slots must behave like other AE2 portable components: hover tooltip, card admission, and card effects.

## Cause and change

AE2 decides slot admission and machine tooltip lines from the static `Upgrades` association table, so third-party items must call `Upgrades.add(card, item, count)`. The addon had never registered any upgrade card for the portable loop cells, which made every card uninsertable and produced no slot hint.

Both adapters now register, for every portable loop tier, the same cards as AE2 portable item/fluid cells:

- Fuzzy card x1
- Inverter card x1
- Equal distribution card x1
- Void card x1
- Energy card x2 (drives the existing charge-rate multiplier)

The loop cell inventory now builds the cell's partition list (config + fuzzy mode) and applies inverter blacklist/whitelist, void overflow acceptance, and equal-distribution per-type byte cap when the corresponding cards are installed. Stationary loop cells remain unfiltered because they are not `ICellWorkbenchItem`.

## Verification

Both builds pass with new contract tests asserting the five registrations plus partition/inverter/void/equal code paths. Clean JAR hashes after this change: 1.21.1 `51865CE1868284AD784C413425F27E1FD530DF248B4764BC83A0E47F875663E4`; 26.1.2 `31AE2711A291598D715AA1A85EA1E24E7FDF2BA1A660161E96CA9431232723BF`.

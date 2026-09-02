# M-tier DISK aggregate capacity

Date: 2026-09-01

## Requested model

M-tier Loop Storage Cells now follow a shared-pool model based on AE2Things Deep Item Storage disK behavior. They do not partition or reserve capacity by storage category. Their aggregate total is exactly 63 times the tier's original single-type ceiling. The infinite tier uses the same aggregate semantics with unlimited amount and unlimited types and does not show a separate type statistic.

## Capacity table

| Tier | Original single-type byte ceiling | Multiplier | Shared byte budget |
| --- | ---: | ---: | ---: |
| 1M | 1,048,576 | 63 | 66,060,288 |
| 4M | 4,194,304 | 63 | 264,241,152 |
| 16M | 16,777,216 | 63 | 1,056,964,608 |
| 64M | 67,108,864 | 63 | 4,227,858,432 |
| 256M | 268,435,456 | 63 | 16,911,433,728 |

All dynamically registered AE key types consume the same aggregate budget through their native amount-per-byte rate. Fractional bytes are combined across key types before the aggregate pool is rounded, so one item fraction and one fluid or energy fraction can share the same byte. There is no per-category allocation and M/infinite tooltips omit the type counter. k tiers retain their AE2-equivalent byte capacities, per-type overhead, and 63-type limit.

## Verification and deployment

- Added mixed item/fluid regressions that fill one M-tier aggregate pool, prove zero type overhead, and prove fractional-byte sharing across categories for M and infinite tiers.
- Minecraft 1.21.1 passes 97 tests; Minecraft 26.1.2 passes 96 tests; no failures, errors, or skips.
- Both release JARs built successfully and were refreshed into the matching PCL instances with unchanged save manifests.
- 1.21.1 SHA-256: `AFA189AF9AB115904C20AB3EB12BA996768256203F6BA0D4287328765BCCB79B`.
- 26.1.2 SHA-256: `0A46940E78410DDE3BC5ED4BA30AE164F5438CA7A1416585261D49D4C0D9E8B6`.

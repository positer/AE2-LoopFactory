# Native factory transport on 26.1.2

Machine routes use NeoForge transactional ResourceHandler and EnergyHandler block capabilities. Explicit faces remain exact; omitted faces consult the unsided view and exposed face handlers. Source snapshots share one quota across the group. Each transfer opens a transaction covering extraction and insertion; changed destination acceptance aborts both endpoints. Ordinary zero-acceptance requests continue, while must obligations retain their remaining amount and continuation.

Additional sided or context-free ResourceHandler capabilities are discovered by their registered capability metadata. Nonempty RegisteredResource values supply their registry type and identifier, so they can be selected without an AE2 key type. Only endpoints with the same capability identity exchange such values. Unknown resource objects without a registered identity need an adapter. Custom capability aliases exposing the same inventory through different names are not a universal deduplication contract.

The explicit source/storage boundary uses native item/fluid identities and an optional AppliedFlux FE bridge. FE is identified by the established appflux:flux / appflux:fe pair; GTEU must not be classified as FE. Pure machine FE transport calls EnergyHandler directly. Additional custom resources are not automatically admitted to AE recipe slots or AE storage.

Tests distinguish actual installed mods from test endpoints: the modern native transaction fixture registers persistent barrel-backed extra resource and FE capabilities in the helper mod, exercising read-only unsided access, directional fallback, changed-acceptance rollback, exclusion, partial must, ordinary skip, native HAS and repeated job codec restoration. A separate real vanilla blast-furnace fixture submits three simultaneous CPU orders through one nonblocking provider/subnet and checks per-order counters. Neither is represented as a modern Mekanism run. The official Modrinth query retained in the dated campaign returned no 26.1.2 Mekanism version.

# 26.1.2 原生工厂物流

机器间物流直接使用 NeoForge 事务式块能力。省略面时查询无面向视图并尝试机器开放的面；显式 on 只查询指定面。接收方改变接受条件时，事务同时回滚本次提取和插入。普通零接受请求继续执行；must 在各任务内保留剩余数量。

额外标准资源处理器可按注册能力元数据发现，并从资源注册身份取得类型和 ID；无需 AE2 自定义存储键。两端必须使用相同能力标识。无法提供注册身份的资源仍需专门适配，多个能力别名是否共享同一库存不能普遍自动推断。AE source/storage 边界仅支持已明确映射的资源，不会自动把额外资源变成 AE 配方输入。

现代版接口测试使用测试附属在真实世界桶方块上注册、由方块持久数据支持的能力，明确区别于第三方模组实测；另外使用原版高炉执行同子网三种非阻挡订单。1.21.1 的 MEK 化学品／FE 实测单独保留。当前官方版本查询没有可用的 26.1.2 MEK 包，因此不宣称在现代版运行过 MEK。

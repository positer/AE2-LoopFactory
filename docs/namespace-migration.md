# AE2LpT 注册域迁移 / Registry migration

本版的 NeoForge 主模组 ID 与资源注册域均为 `ae2lpt`。项目名称与 JAR 前缀仍使用 AE2LpT。开发者在新代码中应使用 `ae2lpt:loop_crystal` 一类的 ID。

升级时先备份世界，用新 JAR 替换旧版。NeoForge 别名将旧物品、方块、方块实体及数据组件映射到同一个新注册项。原有全向网络数据沿用旧文件名。旧配方 ID 在正常查找无结果时回退到新域，因此已编码样板可以继续引用旧配方。需要 `ae2lightoptimizer` 的附属可通过同一 JAR 内的兼容入口加载；Java 包名沿用原名。

覆盖内置配方、材质或模型时，包内目录必须匹配新域。以下命令从旧目录或 ZIP 创建新 ZIP，输入保持原样。仅启用转换后的副本，保留原有包优先级。

```powershell
python tools/migrate_ae2lpt_pack.py ./old-pack.zip ./looptech-pack.zip
```

工具转换资源目录及 JSON、函数、SNBT 文本中的旧域引用。二进制内容原样复制。自定义附属中按字符串比较域名、读取旧资源路径或动态生成配方 ID 的代码应更新为 `ae2lpt`。Lightning Tech 拥有独立的 `ae2lt` 域。

1.21.1 的水晶科技 1.2.1 客户端还需 AE2 Wireless Terminals。兼容测试使用 19.5.1。

## English

The primary NeoForge mod ID and registry namespace are `ae2lpt`. Back up the world and replace the old JAR. Legacy registry aliases read existing stacks, block states, block entities and data components. Saved wireless networks retain their file names. Missing legacy recipe lookups fall back to the new recipe ID. The JAR provides the old loader ID for addon dependencies and keeps the existing Java packages.

Use the command above to copy an old data or resource pack into a new ZIP. Enable the converted pack at its original priority so overrides target the new namespace. Binary payloads are copied unchanged. Update explicit namespace comparisons and resource paths in third-party code.

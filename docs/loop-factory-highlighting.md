# Factory code colors and line comments / 工厂代码高亮与行注释

## English

The native code editor highlights the supported indentation language and SFM syntax. Keywords are blue, strings gold, quantities orange, comments green, machine labels teal, functions purple, operators yellow, resource selectors pale blue, and recipe references pink. Plain text remains light gray. The coloring is tolerant while typing; compiler validation still decides whether a program is valid.

Coverage includes `channel`, conditions and loops, `and` / `or` / `not` / `in`, faces, time units, Unicode label/function declarations and calls, namespace IDs, resource tags, wildcards, selector operators, and both aggregate `P` / `O` and indexed `P0` / `O0` recipe references. SFM additionally colors its case-insensitive triggers, global timer offsets, route clauses, comparison words, absolute sides, and bare resource IDs. This does not add unsupported SFM statements.

In both languages, `//` outside double quotes starts a comment extending to the end of the physical source line. It can immediately follow code without a space. Comment text is ignored during compilation, including quotes or instruction-like text inside it. Original editable source and line numbers are retained. Quoted text such as `"https://example/a"` retains its slash pair. A single slash in a resource path remains part of the ID; an unquoted double slash starts a comment. Existing indentation-language `#` comment lines and SFM `--` comments remain supported. An inline `#c:ingots/iron` resource selector is highlighted as a resource.

```text
import Input, Output // Machine labels
channel // Independent declaration scope
    get 64 minecraft:iron_ingot from Input // Source quota
    put 64 minecraft:iron_ingot into Output // Transfer
done// End
```

```text
// SFM header
NAME "Smelter // A" // The slash pair inside quotes is literal
EVERY 20 TICKS DO // Once per second
    INPUT 1 iron_ingot FROM Src // Resource and label
    OUTPUT 1 iron_ingot TO Dst
END
```

The editor caches whole-source spans after a text change and intersects them with visible native lines. Soft wrapping therefore preserves string, comment, and resource colors; editing invalidates the cache immediately. The render override is limited to the factory code screen.

## 简体中文

原生代码编辑器支持缩进语言与 SFM 已实现语法的高亮：关键字为蓝色，字符串为金色，数量为橙色，注释为绿色，机器标签为青绿色，函数为紫色，运算符为黄色，资源选择器为浅蓝色，配方引用为粉色，普通文字保持浅灰色。输入过程中允许不完整代码着色，代码是否合法仍由编译器判断。

覆盖 `channel`、条件与循环、`and` / `or` / `not` / `in`、方向、时间单位、中文等 Unicode 标签和函数声明及调用、资源 ID、资源标签、通配符、选择器运算符，以及聚合 `P` / `O` 和编号 `P0` / `O0` 配方引用。SFM 还覆盖大小写不敏感的触发器、全局计时偏移、物流子句、比较关键字、绝对方向与省略命名空间的资源 ID。高亮不会新增尚未实现的 SFM 指令。

两种语言均可使用 `//` 注释本行后续内容，也可紧接代码使用，无需空格。注释中的引号或指令文字不会参与编译；原始可编辑代码与报错行号保留。双引号内的 `//` 是字符串内容，例如 `"https://example/a"`。资源路径中的单个 `/` 保留；引号外的两个连续 `/` 开始注释。原有缩进语言的 `#` 整行注释和 SFM 的 `--` 注释继续可用，行内 `#c:ingots/iron` 资源标签按资源显示。

```text
import 输入, 输出 // 机器标签
channel // 独立来源声明范围
    get 64 minecraft:iron_ingot from 输入 // 来源额度
    put 64 minecraft:iron_ingot into 输出 // 实际转运
done// 结束
```

```text
// SFM 开头注释
NAME "熔炉 // A" // 引号内的双斜线保留
EVERY 20 TICKS DO // 每秒执行
    INPUT 1 iron_ingot FROM 输入 // 资源与标签
    OUTPUT 1 iron_ingot TO 输出
END
```

高亮在代码变化后按全文解析并缓存，再按照原生编辑器的可见行裁剪绘制。字符串、注释和长资源 ID 自动折行后保持原来的颜色，修改代码会立即刷新缓存。高亮绘制接管仅限工厂代码界面。

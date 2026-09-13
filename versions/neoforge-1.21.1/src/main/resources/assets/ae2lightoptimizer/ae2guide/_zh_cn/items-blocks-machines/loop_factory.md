---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: 循环工厂
  icon: ae2lightoptimizer:loop_factory_pattern_provider
  position: 905
categories:
- devices
item_ids:
- ae2lightoptimizer:loop_factory_pattern
- ae2lightoptimizer:loop_factory_pattern_provider
- ae2lightoptimizer:handheld_loop_factory_encoder
- ae2lightoptimizer:loop_factory_pattern_encoding_panel
- ae2lightoptimizer:loop_factory_network_terminal
- ae2lightoptimizer:loop_factory_interface_cable
---

# 循环工厂

## 六种部件与首次搭建

循环工厂包含样板、 样板供应器、 手持编码器、 样板编码面板、 网络终端和网络接口线缆。 六种物品的合成表见本页末尾。

供应器箭头朝向独立子网， 其他面连接主 AE 网络。 此边界只自动传递 AE 电， 不合并存储与频道。 机器直接连接完整方块形态的网络接口线缆； 线缆可接 AE 线缆和 AE 机器， 自身不消耗频道。 供应器管理其朝向子网的工厂与标签； 普通工厂网络则需要一个网络终端。 重复终端断开联网， 子网内供应器优先于终端。 配方环解算终端和超算服务也分别限制每网一个有效实例。

在普通 AE2 样板编码终端或循环工厂样板编码面板中配置配方。 工厂面板支持染色， 通过页面按钮切换原生配方页与代码页。 手持编码器只编辑代码。 网络终端只能放入无配方的循环工厂样板。


无配方网络终端在已安装代码发生变化，或工厂网络收到红石上升沿时，取消旧执行并从当前代码开头重新运行。持续供电不会反复重启；保存相同代码、修改未保存草稿均保留进度。更换或移除样板也会取消旧程序。旧等待、must 欠额、来源声明、函数调用与输出红石脉冲会清除；实际缓存资源保留在持久化回收区，已经送达的资源不会撤回。普通重载保留进度，尚未执行的代码刷新请求也会随区块保存。配方供应器订单仍各自执行。机器标签可跨 channel 重叠；需要分流到不同物理容器时，应移除意外的共用绑定。

## 编辑界面与网络绑定

手持编码器右键空气或不可交互方块打开代码页， Shift 使用清除网络绑定。 未绑定时可右键有效工厂网络的线缆、 控制器或终端绑定； 右键供应器绑定的是箭头朝向子网。

标题显示当前设备。 右上角样板槽放入实体循环工厂样板， 回车箭头图标保存代码； 编码面板还提供上传箭头图标和配方工作台页面图标。 悬停可查看当前语言的说明。 标题下方为代码区， 错误以红色显示行号和原因， 较长状态悬停可查看全文。

保存将校验成功的代码写入槽内样板， 并保留设备草稿。 保存失败不覆盖上次有效实体样板。 在面板选择连接主网的供应器后， 可将实体样板上传到其空槽。 上传会按目标子网标签重建 import， 并校验生成的草稿； 缺少标签、 供应器满或代码非法都会阻止上传。 生成的 import 同步显示， 保证报错行号与代码一致。 清空样板与解绑编码器不同： 手持样板本身 Shift 使用， 清除其配方和代码。

## 一个标签绑定多台机器

用 `import A,B` 声明标签。 名称区分大小写， 由字母、 数字、 下划线组成， 首位不能是数字， 不能占用保留字。 修改代码时仍被声明的标签保留原有机器绑定； 删除标签会删除其绑定。 标签保存在网络终端中， 供应器子网的标签则保存在供应器中。

手持已绑定编码器， 按住 Tab 滚动滚轮选择标签， 右键可达机器进行标记； Ctrl 右键同时标记直接连通的同种方块机器。 绑定按位置去重， 同一位置不会重复计数。 机器以整个方块轮廓高亮； 未声明标签时无法选择标签。 当前批量搜索最多访问 4096 个位置， 高亮最多显示 96 格内的 4096 个位置。

`A has minecraft:iron_ingot` 汇总 A 中所有可达机器的铁锭。 指令数量是整组共享额度， 不会对每台机器重复发放。 路由会结合各目标的剩余容量遍历组内机器。 源和目标标签重叠时， 不以端点向自身转运消耗额度； 同一次来源组 put 中刚收到的资源不会再被该组后面的来源重复导出。 机器按位置排序， 但不要依赖通配符匹配出的不同资源类型之间的顺序。

## 来源、 资源与配方

`get` 声明后续 `put` 可以从哪里取得资源， 本身不提取。 普通工厂没有通用实物缓存，`put` 执行直接提取与插入。`storage` 代表绑定 AE 网络的可访问存储； 只有供应器任务拥有 `source`， 包含 CPU 分配材料及受支持的感应卡 FE。`put ... into source` 将产物提交返回主网。

省略数量表示不设上限，按选择器能够到达的全部资源处理； 指定数量时， 额度在匹配资源和机器之间共享。 可通过 `on up`、`down`、`north`、`south`、`east`、`west` 指定交互面。 省略面时先查询无面向接口， 再通过机器实际开放的面执行搬运； 指定面时只查询该面， 不会修改机器的输入输出配置。

### 选择器操作数

| 形式 | 选中范围 | 示例 |
| --- | --- | --- |
| 注册 ID | 单个确定的资源 | `minecraft:iron_ingot` |
| 资源类型 | 该类型全部资源 | `minecraft::item`、`neoforge::fe` |
| 类型加 ID | 某类型下单个 ID | `minecraft::item/minecraft:iron_ingot` |
| 配方材料 | 第 n 个已配置输入 | `P1`、`P2` |
| 配方产物 | 按顺序第 n 个产物 | `O1`、`O2` |
| 配方输入全集 | 本次配方配置的全部材料 | `P` |
| 配方输出全集 | 配方声明的全部产物 | `O` |
| 物品标签 | 携带该标签的物品 | `#minecraft:planks`、`#c:ingots` |
| 通配符 | `*` 任意长度、 `?` 一个字符 | `minecraft:iron_*`、`minecraft:?ron_ingot` |

资源类型使用双冒号： `minecraft::item` 选中物品， `minecraft::fluid` 选中流体， `neoforge::fe` 与 `addon::type` 同理； 需要锁定单个 ID 时写作 `minecraft::item/minecraft:iron_ingot`。 通配符可能同时选中多个资源， 包括填槽物品。 物品标签按实时注册表解析， 跟随数据包与所有已加载模组； 匹配不到东西的标签就是空集合， `#minecraft:stone_crafting_materials` 与 `#minecraft:logs` 是两个实用的原版例子。
物品标签按资源自身类型对应的标签注册表匹配，因此物品标签、流体标签以及模组或数据包添加的标签只要已加载就能直接使用；不存在的标签就是空集合。


### 逻辑算符

| 算符 | 含义 | 示例 |
| --- | --- | --- |
| `&` | 合并：两侧并集 | `P1&O1`、`item&fluid` |
| `!` | 消去右侧 | `item!gold` |
| `( ... )` | 括号分组 | `(A&B)!(C&D)` |
| `!( ... )` 中的 `,` | 反选列表 | `item!(stone,dirt)` |

合并与消去严格从左到右折叠， 因此 `A&B!C&D` 等于 `((A∪B)\C)∪D`； 于是 `(A&B)!(C&D)` 与 `A&B!C!D` 等价， `A!(B,C)` 与 `A!B!C` 等价。 消去只删除资源、 永不新增。 反选本身也可以是完整表达式： `*!(minecraft::fluid,minecraft:*!(minecraft:iron_ingot))` 会保留铁锭而排除其他所有流体。 只要书写资源， 就可以使用选择器： `get`、`put`、`has`、`must` 数量与反选列表。
机器标签也用同一个算符聚合： `get ... from A&B` 与 `put ... into A&B` 作用于两组机器的并集， `redstone A&B 1 tick` 会向该并集内每台机器发一次信号。


### 语句一览

| 语句 | 写法 |
| --- | --- |
| 导入标签 | `import A,B` |
| 命名 | `name "工厂名"` |
| 声明来源 | `get ... from ...` |
| 执行转运 | `put ... into ...` |
| 等待 | `wait 数量 tick` |
| 红石 | `redstone 标签 数量 tick` |
| 条件 | `if 条件 do` |
| 循环 | `while 条件 do` |
| 函数 | `func 名称` 至 `end` |
|物流分组 | `channel` |

| 停止 | `done` |
|跳出循环 | `break` |

声明写法为 `get [must] [数量] 选择器 from 标签、source 或 storage [on 面]`， 转运写法为 `put [must] [数量] 选择器 into 标签、source 或 storage [on 面]`。`source` 是供应器的 CPU 分配与感应卡缓存， `storage` 是绑定网络的可访问存储， 其他名称必须先 `import`。 显式面为 `on up`、`down`、`north`、`south`、`east`、`west`； 省略时先查无面视图， 再使用已暴露的面处理器。 时间单位为 `tick`、`s`、`min`， 1 秒 20 tick， 1 分钟 1200 tick。 每条语句最多一个 `must`， `else` 可接缩进块或同行一条指令。

缩进必须使用空格 标签名与函数名支持任意 Unicode 字母，`存储`、`熔炉组` 等中文名称与 ASCII 名称完全等价；首字符仍然必须是字母或下划线。， 不能用 Tab。 代码上限 65536 字符， 递归上限 64 层调用。 标签、 函数名与 `name` 不能使用保留字： `import`、`name`、`get`、`from`、`put`、`into`、`on`、`has`、`if`、`else`、`do`、`while`、`wait`、`redstone`、`func`、`end`、`done`、`must`、`storage`、`source`、`true`、`false`、`tick`、`s`、`min`。 非法代码会按客户端语言报出行号与原因， 不会猜测执行。

### 配方引用与数量

`Pn` 从 P1 开始代表已配置配方的第 n 个材料， 无配方时不可使用。 `On` 按配方输出顺序引用第 n 个产物： O1 为第一个（主产物）， O2 为第二个， 依此类推。 输出引用支持 get、 put、 has 与反选， 并能与 must 数量及全部逻辑算符组合。 它们只选择资源身份， 不会自动套用配方产出数量。 无配方、 零或非法编号、 超出输出列表均报错， Pn/On 不能用作标签或函数名。 例如 `get must 2 O1 from Furnace` 后接 `put O1 into source`， 表示等待两个主产物并返回。 数量按对应存储类型的原生单位计量， 并受有符号 64 位整数范围限制。 机器物流使用 NeoForge 事务式物品、 流体及 FE 接口； 额外标准处理器若提供资源的注册身份， 可直接发现， 无需 AE2 存储键。 没有注册身份的资源仍需明确适配。

`P` 与 `O` 是配方全集引用。`P` 覆盖本次执行分配到的全部材料， 因此 `get P from source` 声明完整预期输入集合， `put P into Furnace` 一步搬运整个输入集合； `O` 覆盖配方声明的全部产物， 因此 `while Furnace has O < 1 do` 等待产物出现， `put O into source` 返回完整预期输出集合。 二者与 `&`、`!`、 括号及 must 数量的组合方式和单个材料或产物完全一致， 并且都要求带配方的样板：无配方程序会以 `P and O require a recipe-bound pattern` 报错， 而不是静默不匹配。 它们是保留字， 不能用作标签名、 函数名或 `name` 值。

示例： 声明从 Input 整组获取最多 64 个铁锭， 并在当前 tick 路由到 Output：

```text
import Input,Output
get 64 minecraft:iron_ingot from Input
put 64 minecraft:iron_ingot into Output
done
```

熔炉配方应从 `source` 声明输入， 送入已标记熔炉， 显式等待加工， 再把实际产物返回 `source`。 阻挡模式直到本轮代码完整结束、 输入输出缓存及预期产物全部结清后， 才接收下一批并执行代码。 即使主产物已经足量返回， 只要代码尾部仍在运行或等待， 就不能解除阻挡。 副产物、 部分返回和主网已有同类库存同样不能解除。

安装 AppliedFlux 感应卡后， 供应器 source 可缓存最多 1000000 FE， 移除卡时未用 FE 返回主网。 此附属不是模组的必需依赖。

不带 must 义务的物流完全受阻时立即跳过。`put 64 ...` 最多处理 64， 有部分成功即可继续， 完全没有资源被接受时也继续；`put must 64 ...` 必须累计处理满 64 才继续。 已完成数量和执行位置随存档保存， 重试不会重复之前的转运。`get must 64 ...` 仍只声明来源， 将足量要求交给后续输出。 每个独立任务持有自己的流程， 一个熔炉任务等待不会阻塞其他已接收任务。 正常操作可在同一 tick 执行， 受阻等待允许跨 tick。

```text
import Furnace
get must 64 minecraft:stone from Furnace on down
put minecraft:stone into storage
done
```

普通数量适合尽量处理的批次； 后续步骤必须拿到完整数量时使用 must。 刻意持续运行的无配方循环仍须显式 wait 或 redstone。

## 控制流与 tick 语义

普通指令在当前 tick 内依次执行。 只有 `wait 数量 tick|s|min` 和 `redstone 标签 数量 tick|s|min` 显式暂停程序， 两者时长必须为正。 1 秒为 20 tick， 1 分钟为 1200 tick。 红石发往标签中每台机器， 持续时间结束后继续程序； 其他指令没有隐含逐 tick 延迟。

`if 条件 do` 和 `while 条件 do` 控制其下方缩进代码块， 使用空格而不是 Tab。`else` 必须对应 if， 支持下方缩进块或同行一条指令。`has` 返回有符号 64 位数量， 0 为假， 非 0 为真； 比较支持 `<`、`>`、`<=`、`>=`。

### 布尔与比较算符

| 算符 | 含义 | 示例 |
| --- | --- | --- |
| `and` | 两侧都非零 | `A has iron > 0 and B has gold = 0` |
| `or` | 任一侧非零 | `A has iron > 0 or A has gold > 0` |
| `not` | 对其后的布尔取反 | `not A has iron > 0` |
| `<` `>` `<=` `>=` `=` | 比较两个数量 | `Tag has stone >= 64` |
| `( ... )` | 布尔分组 | `(A has iron > 0 or A has gold > 0) and B has stone = 0` |
| `true` / `false` | 布尔字面量 | `true`、`false` |

写在条件中， 例如 `if Input has minecraft:iron_ingot > 0 and Output has minecraft:gold_ingot = 0 do`。`has` 返回有符号 64 位数量， 比较运算返回布尔值： 0 为假， 任何非 0 值为真。 条件可用括号嵌套， 并按书写顺序结合。 有配方程序仍然禁止无条件 `while true`， 该写法只在无配方程序且每条重复路径都经过正时长等待或红石发信时合法。
`has 资源 in 标签` 读取单个机器组，而不带 `in` 的 `has 资源` 统计程序已导入的全部机器标签。两者都接受 `A&B` 这样的聚合组，且同一台机器被多个标签包含时仍只计一次。



有配方样板以完成一轮为目标， 即使循环体带等待也禁止 `while true` 等无条件循环， 并保留有限累计指令预算。 只有无配方代码允许持续运行， 且每条重复执行路径必须实际经过正时长等待或红石发信。 未进入分支、 未调用函数中的 wait， 或内层死循环外的 wait， 不能保护零 tick 真循环。 无进展循环及单 tick 执行保护触发会报错停止， 不会静默跳过指令或自动延后。 合法让出 tick 的无配方程序没有累计生命周期指令上限。
`channel` 块与其他块的排版一致：句首不带 `do`，也不需要结束关键字，正文只需缩进。指南示例中同一资源分别在 channel 与 0 号组中搬运，两者不共享声明。

`channel` 块与其他块的排版一致：句首不带 `do`，也不需要结束关键字，正文只需缩进。指南示例中同一资源分别在 channel 与 0 号组中搬运，两者不共享声明。


`break` 跳出最内层循环并继续执行循环之后的代码；写在所有循环之外（包括程序顶层）时与 `done` 完全相同，直接停止程序。`break` 是保留字，不能作为标签或函数名。

```text
import Input,Output
while true do
    get 64 minecraft:iron_ingot from Input
    put 64 minecraft:iron_ingot into Output
    wait 1 tick
```

`func work` 下方缩进定义函数体， 使用同级 `end` 结束， 单独写 `work` 调用。 声明可放在调用之前或之后， 也可位于条件、 循环或其他函数内； 名称具有程序级作用域， 声明本身不执行函数体。 名称不能重复或占用保留字， 递归最多 64 层调用。`done` 停止整个程序， 在函数内也不是普通函数返回。

```text
work
done
func work
    wait 1 tick
end
```

## 终端执行与 SFM 格式

普通终端代码在红石上升沿启动一次， 持续高电平不会不断触发新轮次。 未完成执行、 等待和标签随所属方块保存。 持续程序放在无配方终端中， 有配方程序必须完成一轮。

目前支持的 SFM 程序包含 EVERY 定时、 tick/秒、 全局偏移或红石脉冲， 以及 INPUT/OUTPUT、 FORGET、 绝对面向、 数量、 引号标签、 排除和布尔条件， 例如：

```text
EVERY 20 TICKS DO
    INPUT 64 minecraft:iron_ingot FROM "Input"
    OUTPUT 64 minecraft:iron_ingot TO "Output"
END
```

SFM 定时程序在终端自动调度。 当前为部分语法兼容， 不是完整 SFM 实现； RETAIN、 EACH、 WITH/WITHOUT、 槽位范围、 轮询和相对面向暂时报错， 不会被默默忽略。

## 排查问题

先检查网络归属、 电量、 供应器箭头、 样板绑定和标签成员。 核对选择器是否排除了填槽物品， 以及 Pn 是否对应有效配方材料。 机器不可用时检查其存储接口及附属集成。 按界面行号和原因修正非法代码； 运行时递归或零 tick 循环会停止并显示错误。 代码最多 65536 字符， 名称最多 128 字符。 长草稿及标签支持持久化， 实际兼容与恢复覆盖范围以验收报告为准。

{/* factory-examples:start */}

## 可直接使用的示例

粘贴前先配置配方模式与标签。 标签名称和配方顺序是示例的一部分。 空转运会等待， 不会静默跳过。


### 过滤后搬运一批

无配方终端。 将 Input 与 Output 绑定到容器。 Input 放入 64 个铁锭和一些金锭。 一次红石上升沿最多搬运 64 个非金锭物品， 金锭保留。 普通数量部分成功即可结束， 完全受阻也跳过。

```text
import Input,Output
get 64 minecraft::item!(minecraft:gold_ingot) from Input
put minecraft::item into Output
done
```

### 等待足量的一批

无配方终端。 Input 放入 64 个铁锭， Output 初始仅留 24 个容量。 等待四 tick 后先转入 24 个， 流程仍停在 put。 再腾出 40 个容量才完成整批 64 个， 存档恢复也不能重复已经转运的部分。

```text
import Input,Output
get must 64 minecraft:iron_ingot from Input
wait 4 tick
put minecraft:iron_ingot into Output
done
```

### 持续嵌套调度

仅用于无配方终端。 Input、 Buffer、 Output 是三个不同容器， Ready 是接收一 tick 红石信号的可达机器。 每轮将一个铁锭经 Buffer 送往 Output， Input 空时每 tick 让出执行。 后续补料继续运行。 只触发一次， 每个新上升沿都会新增独立任务。 更换普通样板不会取消已接收的运行， 断开网络电源可暂停。

```text
import Input,Buffer,Output,Ready
while true do
    if Input has minecraft:iron_ingot > 0 do
        moveOne
    else
        wait 1 tick
    wait 1 tick
func moveOne
    get must 1 minecraft:iron_ingot from Input
    put minecraft:iron_ingot into Buffer
    while Buffer has minecraft:iron_ingot > 0 do
        get must 1 minecraft:iron_ingot from Buffer
        put minecraft:iron_ingot into Output
    redstone Ready 1 tick
end
```

### 有限嵌套调度

无配方终端。 Input 最多放入 128 个铁锭， Buffer 与 Output 留空， 并绑定 Ready。 后置声明的函数等待、 经中间容器转运并发送红石。 Input 空或 Output 达到 128 时结束。 配方内条件循环必须实际结束， 有配方禁止字面量 while true。

```text
import Input,Buffer,Output,Ready
while Input has minecraft:iron_ingot > 0 do
    if Output has minecraft:iron_ingot < 128 do
        moveOne
    else
        done
done
func moveOne
    get must 1 minecraft:iron_ingot from Input
    wait 1 tick
    put minecraft:iron_ingot into Buffer
    get must 1 minecraft:iron_ingot from Buffer
    put minecraft:iron_ingot into Output
    redstone Ready 1 tick
end
```

### 两级配方： 圆石变平滑石

配方供应器。 配置 P1 为一个圆石， O1 为一个平滑石。 StageOne、 StageTwo 分别绑定两组熔炉， 从外部预先供燃料。 流程为 source → 第一组熔炉 → 石头 → 第二组熔炉 → O1 → source。 每级受阻都会等待， 阻挡模式还会等待最后两 tick 代码结束。

```text
import StageOne,StageTwo
feed
get must 1 minecraft:stone from StageOne on down
put minecraft:stone into StageTwo on up
get must 1 O1 from StageTwo on down
put O1 into source
wait 2 tick
done
func feed
    get must 1 P1 from source
    put P1 into StageOne on up
end
```

### 两个输入与两个产物

配方供应器。 依次配置 P1 为一个粗铁， P2 为一个粗金， O1 为一个铁锭， O2 为一个金锭。 IronFurnace 与 GoldFurnace 绑定不同的供燃料熔炉组。 特意先返回金锭， 不能因此提前解除阻挡， 还要等铁锭与代码尾部。 On 只表示产物资源， 不自动代表配方数量。

```text
import IronFurnace,GoldFurnace
get must 1 P1 from source
put P1 into IronFurnace on up
get must 1 P2 from source
put P2 into GoldFurnace on up
get must 1 O2 from GoldFurnace on down
put O2 into source
get must 1 O1 from IronFurnace on down
put O1 into source
wait 2 tick
done
```

### SFM 定时与红石组合

无配方终端。 Input 放入铁锭、 金锭， Output 留空。 有铁锭时每两 tick 搬运一个， 每次红石上升沿搬运一个金锭， 持续高电平不会重复触发。 同一个运行内部按顺序执行， 受阻指令保留该流程， 不是并行分支。 此例用条件避免在空来源等待。

```text
EVERY 2 TICKS DO
    IF "Input" HAS GT 0 minecraft:iron_ingot THEN
        INPUT MUST 1 minecraft:iron_ingot FROM "Input"
        OUTPUT minecraft:iron_ingot TO "Output"
    END
END
EVERY REDSTONE PULSE DO
    IF "Input" HAS GT 0 minecraft:gold_ingot THEN
        INPUT MUST 1 minecraft:gold_ingot FROM "Input"
        OUTPUT minecraft:gold_ingot TO "Output"
    END
END
```

### SFM 有限配方

配方供应器。 配置一个圆石产出一个石头， 绑定 Furnace 并预先供燃料。 INPUT 声明本次分配的 P1， OUTPUT 从顶部进料， FORGET 清除旧来源， 再从底部取得 O1 并返回。 本轮材料与产物结清后结束， 不作为无配方定时器永久运行。

```text
EVERY TICK DO
    INPUT MUST 1 P1 FROM source
    OUTPUT P1 TO Furnace TOP SIDE
    FORGET
    INPUT MUST 1 O1 FROM Furnace BOTTOM SIDE
    OUTPUT O1 TO source
END
```

{/* factory-examples:end */}

<RecipeFor id="ae2lightoptimizer:loop_factory_pattern" />

<RecipeFor id="ae2lightoptimizer:loop_factory_pattern_provider" />

<RecipeFor id="ae2lightoptimizer:handheld_loop_factory_encoder" />

<RecipeFor id="ae2lightoptimizer:loop_factory_pattern_encoding_panel" />

<RecipeFor id="ae2lightoptimizer:loop_factory_network_terminal" />

<RecipeFor id="ae2lightoptimizer:loop_factory_interface_cable" />

若 `get`/`put` 无搬运且界面无报错， 先核对选择器： 资源类型必须用双冒号（ `minecraft::item`、`minecraft::fluid`）， 单冒号会被当作注册 ID（ `minecraft:oak_log`）因而匹配不到任何资源并被静默跳过。

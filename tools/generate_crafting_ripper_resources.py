"""Generate generation-native Crafting Ripper and Loop Card resource wiring."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOD = "ae2lpt"

GUIDES = {
    "crafting_ripper": (
        "Crafting Ripper", "合成撕裂者",
        """<BlockImage id="ae2lpt:crafting_ripper" scale="6" />

<RecipeFor id="ae2lpt:crafting_ripper" />

Connect the Crafting Ripper to an ME Network with an available channel and power,
then right-click it. Its interface reuses AE2's Pattern Provider controls and
priority page with a scrollable four-row window. Pattern storage grows as needed,
with one completely empty row after the last occupied row. Insert crafting-table,
smithing-table or stonecutter patterns; other processing patterns are rejected.

## Whole-chain crafting

Request the final item through your ME Crafting Terminal as usual. The ripper
checks every recipe in the selected chain before reserving ingredients, checks
the real selected inputs again before execution, and produces the chain's result
in one server tick. Container returns and other recipe remainders are retained.

The [Recipe Ring Solver Terminal](recipe_ring_solver_terminal.md) still owns
cyclic planning. Its compressed execution order and initial seeds are preserved
when the ripper executes the chain. Quantities use checked signed 64-bit values.
Unsupported or stale recipes cannot partially consume a chain.

The machine draws no idle power of its own and charges no AE for a chain, like a
native AE2 pattern provider. It is treated as infinite parallel crafting: a whole chain
commits in one tick regardless of the CPU's co-processors.
Delivery can wait when the requester is full; it does not repeat the craft or
charge again. The face is cyan while its grid node is active and dim while offline.

## Automatic patterns

Insert a [Loop Card](loop_card.md) into the upgrade slot to advertise encodable
crafting-table, smithing and stonecutting recipes from the server's recipe list.
Manual patterns remain editable, including through the Pattern Access Terminal.
For each exact output covered by an installed pattern, only the given patterns
are advertised: missing ingredients never trigger another automatic recipe for
that output. All other encodable outputs retain their automatic recipes.

Automatic patterns are backed by real matching inputs and assembled outputs.
Dynamic recipes with no finite encodable representative, such as arbitrary dye
or component combinations, still require a concrete encoded pattern.

For example, install a plank pattern and a stick pattern, then request sticks.
The ripper validates both steps and consumes the available logs for the final
sticks in one execution. Adding the ring terminal allows supported template
growth chains to preserve their seed while delivering only the requested gain.

## Import network patterns

Click the down-arrow button beside the pattern area to move supported encoded patterns from ME storage and installed pattern slots on the same network, including other rippers. Processing, invalid and stale patterns stay in their source. Existing patterns in this ripper stay in place. Imports move the real items in batches; no patterns are created. Keep the screen open until import finishes. Closing the screen or losing the network stops further transfers; completed transfers remain in the ripper.
""",
        """<BlockImage id="ae2lpt:crafting_ripper" scale="6" />

<RecipeFor id="ae2lpt:crafting_ripper" />

将合成撕裂者接入有可用频道与电力的 ME 网络，右键打开界面。
界面复用 AE2 样板供应器控件与优先级页面，使用四行可滚动窗口。
样板库存按需扩容，最后一个已占用行后始终保留一整行空槽。
可放入工作台、锻造台和切石机样板，其他加工样板不会被接受。

## 整条链合成

像平常一样在 ME 合成终端请求最终物品。撕裂者在预留材料前检查所选链的每个配方，
执行前再次核对真实输入，然后在一个服务端 tick 内产出整条链的结果。
容器与其他配方返还物均会保留。

[配方环解算终端](recipe_ring_solver_terminal.md)继续负责循环计划。
撕裂者执行时遵循其压缩顺序并保留初始种子，数量使用带溢出检查的有符号 64 位数。
不支持或已失效的配方不会先消耗部分合成链材料。

自身没有待机耗电，也不收取合成费用，与原生 AE2 样板供应器一致；并被视为无限并行合成：
整条链不受 CPU 协处理器数量限制，在一个 tick 内提交。接收方已满时可以延后交付，不会重新合成或重复收费。
网络节点激活时核心呈青色，离线时变暗。

## 自动样板

在升级槽安装[循环卡](loop_card.md)，即可从服务端配方列表发布可编码的工作台、
锻造台与切石机配方。手动样板仍可正常放取，样板管理终端也可编辑。
对于槽内样板指定的目标产物，仅发布这些给定样板；即使缺料，也不会切换到
同产物的自动配方。其他目标产物继续发布全部可编码的自动配方。

自动样板必须有真实匹配的输入与组装结果。没有有限可编码输入代表的动态配方，
例如任意染色或组件组合，仍需使用具体编码样板。

例如放入木板与木棍样板后请求木棍，撕裂者会检查两步配方并在一次执行中消耗原木、
产出最终木棍。配合环解算终端执行受支持的模板增长链时，会保留种子，只交付所需净增长。

## 导入网络样板

点击样板区右侧的向下箭头按钮，可将 ME 存储和同网络设备样板槽中的有效样板移入撕裂者，也包括其他撕裂者内的样板。仅接收合成台、锻造台和切石机样板，加工、失效或无效样板留在原处；当前撕裂者内的样板保持原位。导入分批移动真实物品，不生成新样板。请保持界面打开直到完成；关闭界面或网络断开会停止后续转移，已完成的部分保留。
"""),
    "loop_card": (
        "Loop Card", "循环卡",
        """<ItemImage id="ae2lpt:loop_card" scale="4" />

<RecipeFor id="ae2lpt:loop_card" />

Craft one Advanced Card and one Loop Crystal together in any arrangement.
The Loop Card fits a [Crafting Ripper](crafting_ripper.md) or any
[Portable Loop Storage Cell](loop_storage_cells.md). Only one is needed per device.

## Crafting Ripper

The card publishes automatically discovered, encodable crafting, smithing and
stonecutting recipes. Manual patterns remain editable in the scrollable inventory.
Installed patterns exclusively select recipes for their exact outputs, including
components; automatic alternatives for those outputs are excluded even when
manual ingredients are missing. Other outputs retain all encodable automatic recipes.

## Portable charging

Install the card in a portable cell's existing upgrade slots. Stored FE is then
converted into its AE battery using AE2's configured unit conversion and native
charge limit. It can recharge an empty AE battery. No stored FE is created, and
charging stops when the battery is full or the card is removed.

An AE2 energy-storage addon must provide the FE storage key. AE2LF does not add
a new required mod. For example, store FE in a portable cell, install a Loop Card
and carry it: the card consumes the stored FE as the terminal battery recharges.

## Portable capacitor

A portable cell with **stored FE and positive AE charge** also exposes its FE
through NeoForge's item energy capability. Other mods that query or extract from
this standard capability can use it as a carried energy source. This does not
require a Loop Card; the card supplies automatic AE recharging.

At zero AE charge the capacitor is unavailable until the cell is recharged.
Legacy integer energy queries saturate at their API maximum while the real stored
amount remains 64-bit; the newer energy interface reports long amounts directly.

## 导入网络样板

点击样板区右侧的向下箭头按钮，可将 ME 存储和同网络设备样板槽中的有效样板移入撕裂者，也包括其他撕裂者内的样板。仅接收合成台、锻造台和切石机样板，加工、失效或无效样板留在原处；当前撕裂者内的样板保持原位。导入分批移动真实物品，不生成新样板。请保持界面打开直到完成；关闭界面或网络断开会停止后续转移，已完成的部分保留。
""",
        """<ItemImage id="ae2lpt:loop_card" scale="4" />

<RecipeFor id="ae2lpt:loop_card" />

将一个高级卡与一个循环水晶任意摆放，即可无序合成循环卡。
循环卡可安装在[合成撕裂者](crafting_ripper.md)或任意档位的
[便携循环存储磁盘](loop_storage_cells.md)中，每台设备只需一张。

## 合成撕裂者

安装后发布自动发现、可编码的工作台、锻造台与切石机配方。
样板页支持滚动和自动扩容，插卡后仍可正常放取样板。
槽内样板对应的目标产物（含组件）只使用给定样板，缺料也不自动换配方；
未被手动样板覆盖的产物继续发布全部可编码自动配方。

## 便携充能

在便携磁盘已有的升级槽安装循环卡后，已存储的 FE 会按 AE2 配置的转换比例
与原生充能上限转成自身 AE 电量，也可以从空 AE 电池开始充电。
不会凭空产生 FE；电池充满或卡被取出后停止转换。

需要由 AE2 能量存储附属提供 FE 存储键，AE2LF 不新增硬前置。
例如先在便携磁盘中存入 FE，再安装循环卡并随身携带，卡会消耗内部 FE 为终端电池补电。

## 随身电容器

便携磁盘在**存有 FE 且 AE 电量大于零**时，通过 NeoForge 原生物品能量能力暴露 FE。
其他模组使用这一标准能力查询或提取电量时，可以把它作为随身能源。
此能力本身不要求循环卡，循环卡负责自动补充 AE 电量。

AE 电量归零后，电容器暂不可用，重新充电即可恢复。
旧版整数能量接口的查询值在其上限截断，但真实存储仍保持 64 位数量；
新版能量接口直接报告 long 数量。
"""),
}


# Match native AE2 slot geometry while fitting the minimum 240 logical pixel viewport.
CRAFTING_RIPPER_SCREEN = {'includes': ['common/common.json', 'common/player_inventory.json'],
 'generatedBackground': {'width': 194, 'height': 210},
 'slots': {'ENCODED_PATTERN': {'left': 8, 'top': 45, 'grid': 'BREAK_AFTER_9COLS'},
           'STORAGE': {'hidden': True},
           'PLAYER_INVENTORY': {'left': 8, 'top': 129, 'grid': 'BREAK_AFTER_9COLS'},
           'PLAYER_HOTBAR': {'left': 8, 'top': 187, 'grid': 'HORIZONTAL'}},
 'text': {'dialog_title': {'text': {'translate': 'block.ae2lpt.crafting_ripper'},
                           'position': {'left': 8, 'top': 6}},
          'interface_config': {'text': {'translate': 'gui.ae2.Patterns'}, 'position': {'left': 8, 'top': 34}},
          'player_inventory_title': {'text': {'translate': 'container.inventory'},
                                     'position': {'left': 8, 'top': 118}}},
 'widgets': {'importPatterns': {'left': 170, 'top': 24},
             'openPriority': {'left': 170, 'top': 0, 'width': 20, 'height': 20},
             'lockReason': {'left': 5, 'top': 15},
             'patternScroll': {'left': 176, 'top': 45, 'width': 12, 'height': 70}}}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


for generation in ("1.21.1", "26.1.2"):
    root = ROOT / f"versions/neoforge-{generation}/src/main/resources"
    assets = root / "assets" / MOD
    data = root / "data" / MOD
    write(root / "assets/ae2/screens/ae2lightoptimizer_crafting_ripper.json", CRAFTING_RIPPER_SCREEN)
    ingredient = (lambda item: {"item": item}) if generation == "1.21.1" else (lambda item: item)
    loop_crystals = {'tag': 'c:gems/loop_crystal'} if generation == '1.21.1' else '#c:gems/loop_crystal'
    write(data / "recipe/crafting_ripper.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["ISI", "FCD", "ISI"],
        "key": {key: ingredient(item) for key, item in {
            "I": "minecraft:iron_ingot", "S": "ae2:quantum_entangled_singularity",
            "F": "ae2:formation_core", "C": f"{MOD}:256m_loop_storage_core",
            "D": "ae2:annihilation_core"}.items()},
        "result": {"id": f"{MOD}:crafting_ripper", "count": 1}})
    write(data / "recipe/loop_card.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [ingredient("ae2:advanced_card"), loop_crystals],
        "result": {"id": f"{MOD}:loop_card", "count": 1}})
    write(assets / "blockstates/crafting_ripper.json", {"variants": {
        f"connected={str(connected).lower()}": {
            "model": f"{MOD}:block/crafting_ripper" + ("_connected" if connected else "")}
        for connected in (False, True)}})
    for suffix in ("", "_connected"):
        write(assets / f"models/block/crafting_ripper{suffix}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"{MOD}:block/crafting_ripper{suffix}"}})
    write(assets / "models/item/crafting_ripper.json", {"parent": f"{MOD}:block/crafting_ripper"})
    write(assets / "models/item/loop_card.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/loop_card"}})
    if generation == "26.1.2":
        for item in ("crafting_ripper", "loop_card"):
            write(assets / f"items/{item}.json", {
                "model": {"type": "minecraft:model", "model": f"{MOD}:item/{item}"}})
    write(data / "loot_table/blocks/crafting_ripper.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"{MOD}:crafting_ripper"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    tag = root / "data/minecraft/tags/block/mineable/pickaxe.json"
    values = json.loads(tag.read_text(encoding="utf-8"))
    if f"{MOD}:crafting_ripper" not in values["values"]:
        values["values"].append(f"{MOD}:crafting_ripper")
    write(tag, values)
    for language, title, card in (("en_us", "Crafting Ripper", "Loop Card"),
                                  ("zh_cn", "合成撕裂者", "循环卡")):
        path = assets / f"lang/{language}.json"
        values = json.loads(path.read_text(encoding="utf-8"))
        values.update({f"block.{MOD}.crafting_ripper": title, f"item.{MOD}.loop_card": card})
        write(path, values)
    print(f"Generated {generation}: crafting ripper, advanced-card recipe, models, tags and names")
    for item, (english_title, chinese_title, english_body, chinese_body) in GUIDES.items():
        for prefix, title, body in (("", english_title, english_body), ("_zh_cn/", chinese_title, chinese_body)):
            path = assets / f"ae2guide/{prefix}items-blocks-machines/{item}.md"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes((f"---\nnavigation:\n  parent: ae2:items-blocks-machines/items-blocks-machines-index.md\n"
                f"  title: {title}\n  icon: {MOD}:{item}\n  position: {904 if item == 'crafting_ripper' else 905}\n"
                f"categories:\n- devices\nitem_ids:\n- {MOD}:{item}\n---\n\n# {title}\n\n{body}").encode("utf-8"))

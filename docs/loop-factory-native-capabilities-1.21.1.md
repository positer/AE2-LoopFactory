# Native capability transport — NeoForge 1.21.1

Machine tags use NeoForge block capabilities directly. No Applied Mekanistics or AppliedFlux installation, AE2 chemical key registration, or AE storage cell is required for machine-to-machine chemical/FE transfer. AE identity remains relevant at explicit `storage` / provider `source` boundaries and for recipe Pn/On references.

| Selector | Native capability | Resource example |
| --- | --- | --- |
| `minecraft::item` | `Capabilities.ItemHandler.BLOCK` | `minecraft:iron_ingot` |
| `minecraft::fluid` | `Capabilities.FluidHandler.BLOCK` | `minecraft:water` |
| `neoforge::fe` | `Capabilities.EnergyStorage.BLOCK` | `neoforge:fe` |
| `mekanism::chemical` | MEK `mekanism:chemical_handler` BlockCapability | `mekanism:oxygen` |

MEK is an optional, lazily resolved capability adapter. Additional arbitrary capability interfaces need an adapter; their method semantics cannot be inferred just from a registry identifier. This legacy adapter and actual MEK campaign target 1.21.1; the modern transactional adapter is described in [the 26.1.2 guide](loop-factory-native-capabilities-26.1.2.md).

An explicit `on north` queries only that face. With `on` omitted, the unsided view is queried first and exposed face handlers may perform the operation. MEK marks its unsided proxies read-only, so this fallback is required for ordinary configured machines. Simulated acceptance from aliased face handlers is not added together. Disabled input/output configurations are not silently rewritten. Configure machine sides normally.

GET stores declarations, not physical contents. PUT negotiates and transfers actual resources from source to destination. Ordinary quantities advance after partial success or zero progress. Only must obligations wait. `must` accumulates actual accepted amounts across ticks. Source group snapshots prevent newly delivered stock from being repeatedly relayed in the same instruction. Distinct admitted jobs retain independent continuations. Native quantities and pending routes survive job codec restoration without introducing a custom AE key type.

## Chemical exclusion and mandatory transfer

Bind `ChemSource` to oxygen source tanks and a hydrogen source tank. Bind `ChemTarget` to receiving tanks. Hydrogen remains untouched:

```text
import ChemSource,ChemTarget
get must 30000 mekanism::chemical!(mekanism:hydrogen) from ChemSource
wait 1 tick
put must 30000 mekanism::chemical!(mekanism:hydrogen) into ChemTarget
done
```

If only 1,000 units fit initially, the instruction waits for the remaining 29,000. This example's tanks contain only oxygen/hydrogen; a broad chemical selector would otherwise also include other nonexcluded chemicals. Use `mekanism:oxygen` for an oxygen-only request.

## FE through the standard energy capability

```text
import EnergySource,EnergyTarget
get must 100000 neoforge::fe from EnergySource
wait 1 tick
put must 100000 neoforge::fe into EnergyTarget
done
```

A target with 4,096 FE of initial room receives only that portion; the same job resumes after capacity opens. `EnergySource has neoforge::fe` and `ChemSource has mekanism::chemical` query native quantities.

## Processing setup and evidence

The native fixture places actual MEK blocks with normal placed-item components, enables their ordinary automatic sorting and installs eight native speed/energy upgrades. It does not change MEK recipes, upgrade multipliers or processing duration. Four enriching plus four smelting factories execute real AE2 CPU orders. A raw iron/gold block produces twelve dust, then twelve ingots. Code, quantities, completed runs and limitations belong in the dated campaign report; this guide does not treat pending tests as accepted results.

# 中文

机器标签之间直接调用 NeoForge 的物品、流体、FE 和 MEK 化学品块能力。化学品与 FE 的机器间转运不依赖 Applied Mekanistics、AppliedFlux 或 AE2 额外存储类型注册。`storage`／供应器 `source` 仍属于 AE 网络与配方边界，不能把未注册资源直接当作 AE 合成材料或网络存储资源。

省略 `on` 时读取无方向视图，并尝试方块开放的方向接口；MEK 无方向代理只读，不能仅凭这个代理判断机器不能转运。显式 `on` 严格使用指定方向。不会把多个别名方向的模拟容量相加，也不会修改机器禁用的面配置。

以上示例分别说明化学品反选、30,000 单位足量搬运和 100,000 FE 足量搬运。普通请求完全受阻也跳过；must 完全受阻等待，部分完成后只补剩余数量。get 不拿走实物，各次派发独立持有进度。此处为 1.21.1 的旧版能力适配；26.1.2 的事务式接口另见对应指南。其他任意能力类型仍需明确语义，不能仅凭一个注册名推断存取方法。


## Concurrent recipe example / 并行配方示例

For an iron recipe configured as one raw iron block to twelve iron ingots, the following code uses a shared Enrich tag and shared Smelt tag. Configure separate gold/copper patterns with their own input/output recipes and replace the dust ID accordingly. Submit their orders to separate native CPUs on the provider's main grid with blocking disabled; all patterns remain bound to the same provider subnet. `Pn`/`On` resolve within each admitted job. Use must on productive steps when the recipe must deliver its complete output.

```text
import Enrich,Smelt
get must 1 P1 from source
put must 1 P1 into Enrich
get must 12 mekanism:dust_iron from Enrich
put must 12 mekanism:dust_iron into Smelt
get must 12 O1 from Smelt
put must 12 O1 into source
wait 5 tick
done
```

配方页配置“1 个粗铁块 → 12 个铁锭”，代码页使用以上代码。金、铜样板分别配置对应配方并替换粉末 ID，共用同一供应器子网的 Enrich、Smelt 标签。关闭阻挡模式，从主网的独立合成 CPU 同时提交不同订单；每次派发中的 Pn、On 和 must 剩余量独立。普通请求可以跳过，无法保证配方产物足量返回；需要保证完成的加工步骤应使用 must。代码结束仍不会免除未结清的配方材料／产物义务。

# Loop Factory channels / 物流通道

`channel` is an indentation-based logistics scope, highlighted as a keyword. It isolates GET declarations and their remaining quotas from sibling blocks and from the default channel outside those blocks. A PUT can use only declarations in its own channel. `channel` is reserved and cannot be a tag or function name.

Ordinary instructions in a function inherit the calling channel, including nested calls, MUST waits and saved continuations. An explicit channel block written inside a function keeps its own lexical channel. Returning from the function restores the caller's scope. Each job owns its own declarations and continuation.

```text
import A,B,Output
func send
    put 3 minecraft:iron_ingot into Output
end
get 3 minecraft:iron_ingot from A
channel
    get 3 minecraft:iron_ingot from B
    send
done
```

This moves three iron from B. The function does not use A's default-channel declaration.

Channels do not create separate physical inventories, reserve stock, or start parallel threads. Two channels explicitly reading the same chest still compete for its real contents. A pending MUST or wait pauses subsequent statements in the same job. `has` queries the actual selected machine/storage inventory, not an imaginary per-channel stockpile. These script scopes are separate from AE2 network channels. SFM syntax is a separate compiler mode; this describes the indentation language.

`channel` 是按缩进划分的物流作用域，显示为关键字。它隔离 GET 来源声明及剩余额度；兄弟通道与块外默认通道不能相互借用，PUT 只使用所属通道的声明。`channel` 是保留字，不能再用作标签或函数名。

函数中的普通语句继承调用处通道，嵌套调用、MUST 等待和保存恢复均保留归属；函数内部显式书写的 channel 块仍使用其自身词法通道。函数返回后恢复调用方作用域。不同任务的来源与执行状态各自独立。上面的例子从 B 搬运三个铁锭，不会读取 A 的块外来源声明。

通道不创建独立实物库存、不预留物资，也不启动并行线程。两个通道若都显式声明同一个箱子，仍会竞争箱内实物；当前 MUST 或 wait 会暂停该任务的后续语句。`has` 查询所选机器／存储的实际库存。这与 AE2 网络通道不是同一概念，且本页描述缩进语言，不代表 SFM 模式新增 CHANNEL 语法。


## Terminal refresh / 终端刷新

A recipe-free Network Terminal cancels its old execution and starts the current installed program from the beginning whenever its code changes or its factory network receives a redstone rising edge. A held signal does not repeatedly restart it; saving identical code or changing an unsaved draft preserves progress. Replacing or removing its pattern also cancels the previous program. Old WAIT/MUST debt, source declarations, function calls and outgoing pulses are cleared. Physically buffered resources remain in a persistent recovery queue; already delivered resources are not rolled back. Ordinary reloads preserve progress, while a pending code-change restart survives unloading. Recipe-provider orders retain their independent execution. Machine tags can overlap across channels: remove accidental shared bindings when two channels must address different physical inventories.

无配方网络终端在已安装代码发生变化，或工厂网络收到红石上升沿时，取消旧执行并从当前代码开头重新运行。持续供电不会反复重启；保存相同代码、修改未保存草稿均保留进度。更换或移除样板也会取消旧程序。旧等待、must 欠额、来源声明、函数调用与输出红石脉冲会清除；实际缓存资源保留在持久化回收区，已经送达的资源不会撤回。普通重载保留进度，尚未执行的代码刷新请求也会随区块保存。配方供应器订单仍各自执行。机器标签可跨 channel 重叠；需要分流到不同物理容器时，应移除意外的共用绑定。

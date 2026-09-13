"""Keep runnable examples identical in both native guides and the test fixture catalogue."""
from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[2]
examples=json.loads((root/'tools/runtime-factory-probe/examples/catalog.json').read_text(encoding='utf-8'))
en=[
('Move a filtered batch','Recipe-free terminal. Bind Input and Output to inventories. Put 64 iron and some gold in Input. One rising edge moves up to 64 non-gold items; gold stays. Ordinary partial success may finish; zero success waits.'),
('Wait for an exact batch','Recipe-free terminal. Input contains 64 iron. Output initially has room for only 24. After the four-tick wait, 24 move and the job remains on PUT. Free room for 40 more: the job then finishes at exactly 64, including after save/reload.'),
('Continuous nested dispatch','Recipe-free terminal only. Input, Buffer and Output are three separate inventories; Ready is a reachable machine receiving a one-tick signal. Each iteration transfers one iron through Buffer to Output. Empty input yields every tick. Supply more iron later to continue. Trigger once: each new rising edge starts another independent run. Replacing a custom pattern does not cancel an already admitted run; disconnect network power to pause it.'),
('Finite nested dispatch','Recipe-free terminal. Put up to 128 iron in Input, leave Buffer and Output empty, and bind Ready. The forward-declared function waits, routes through Buffer and pulses Ready. It exits when Input is empty or Output reaches 128. A conditional recipe loop must actually terminate; a literal while true is forbidden on recipes.'),
('Two-stage recipe: cobblestone to smooth stone','Recipe provider. Configure one cobblestone input (P1) and one smooth-stone output (O1). Bind StageOne and StageTwo to two separate furnace groups and supply fuel externally. Flow: source -> StageOne -> stone -> StageTwo -> O1 -> source. Each blocked stage waits. Blocking admission remains closed through the final two-tick code tail.'),
('Two inputs and two outputs','Recipe provider. Configure P1 = one raw iron, P2 = one raw gold, O1 = one iron ingot, O2 = one gold ingot in that order. Bind IronFurnace and GoldFurnace to separate fueled groups. Gold is returned first deliberately; it must not release blocking before iron and the code tail finish. Output references identify resources, not automatically their recipe amounts.'),
('SFM timer plus redstone','Recipe-free terminal. Input has iron and gold; Output is empty. The timer moves one iron every two ticks while available. Each rising edge moves one gold; a held signal does not repeat. Clauses inside one run execute in order: a blocked clause retains that run, not a parallel branch. Guards prevent this example from waiting on an empty input.'),
('SFM finite recipe','Recipe provider. Configure one cobblestone -> one stone, bind Furnace and add external fuel. INPUT declares the assigned P1, OUTPUT feeds the top, FORGET clears the old declarations, and O1 is extracted from the bottom and returned. The recipe run ends when assigned resources and outputs are settled; it is not an endless standalone timer.')]
zh=[
('过滤后搬运一批','无配方终端。 将 Input 与 Output 绑定到容器。 Input 放入 64 个铁锭和一些金锭。 一次红石上升沿最多搬运 64 个非金锭物品， 金锭保留。 普通数量部分成功即可结束， 完全受阻则等待。'),
('等待足量的一批','无配方终端。 Input 放入 64 个铁锭， Output 初始仅留 24 个容量。 等待四 tick 后先转入 24 个， 流程仍停在 put。 再腾出 40 个容量才完成整批 64 个， 存档恢复也不能重复已经转运的部分。'),
('持续嵌套调度','仅用于无配方终端。 Input、 Buffer、 Output 是三个不同容器， Ready 是接收一 tick 红石信号的可达机器。 每轮将一个铁锭经 Buffer 送往 Output， Input 空时每 tick 让出执行。 后续补料继续运行。 只触发一次， 每个新上升沿都会新增独立任务。 更换普通样板不会取消已接收的运行， 断开网络电源可暂停。'),
('有限嵌套调度','无配方终端。 Input 最多放入 128 个铁锭， Buffer 与 Output 留空， 并绑定 Ready。 后置声明的函数等待、 经中间容器转运并发送红石。 Input 空或 Output 达到 128 时结束。 配方内条件循环必须实际结束， 有配方禁止字面量 while true。'),
('两级配方： 圆石变平滑石','配方供应器。 配置 P1 为一个圆石， O1 为一个平滑石。 StageOne、 StageTwo 分别绑定两组熔炉， 从外部预先供燃料。 流程为 source → 第一组熔炉 → 石头 → 第二组熔炉 → O1 → source。 每级受阻都会等待， 阻挡模式还会等待最后两 tick 代码结束。'),
('两个输入与两个产物','配方供应器。 依次配置 P1 为一个粗铁， P2 为一个粗金， O1 为一个铁锭， O2 为一个金锭。 IronFurnace 与 GoldFurnace 绑定不同的供燃料熔炉组。 特意先返回金锭， 不能因此提前解除阻挡， 还要等铁锭与代码尾部。 On 只表示产物资源， 不自动代表配方数量。'),
('SFM 定时与红石组合','无配方终端。 Input 放入铁锭、 金锭， Output 留空。 有铁锭时每两 tick 搬运一个， 每次红石上升沿搬运一个金锭， 持续高电平不会重复触发。 同一个运行内部按顺序执行， 受阻指令保留该流程， 不是并行分支。 此例用条件避免在空来源等待。'),
('SFM 有限配方','配方供应器。 配置一个圆石产出一个石头， 绑定 Furnace 并预先供燃料。 INPUT 声明本次分配的 P1， OUTPUT 从顶部进料， FORGET 清除旧来源， 再从底部取得 O1 并返回。 本轮材料与产物结清后结束， 不作为无配方定时器永久运行。')]
for gen in ('1.21.1','26.1.2'):
 for locale,descriptions in (('',en),('_zh_cn/',zh)):
  p=root/f'versions/neoforge-{gen}/src/main/resources/assets/ae2lightoptimizer/ae2guide/{locale}items-blocks-machines/loop_factory.md'
  text=p.read_text(encoding='utf-8'); start='{/* factory-examples:start */}'; end='{/* factory-examples:end */}'
  text=re.sub(r'<!-- factory-examples:start -->.*?<!-- factory-examples:end -->\n*','',text,flags=re.S)
  text=re.sub(re.escape(start)+r'.*?'+re.escape(end)+r'\n*','',text,flags=re.S)
  blocks=[start,'## '+('Runnable examples' if not locale else '可直接使用的示例'),('Configure recipe mode and tags before pasting. Tag names and recipe order are part of each example. Empty transfers wait; they are not silently skipped.' if not locale else '粘贴前先配置配方模式与标签。 标签名称和配方顺序是示例的一部分。 空转运会等待， 不会静默跳过。')]
  for example,(title,description) in zip(examples,descriptions):
   blocks.extend(['### '+title,description,'```text\n'+example['code']+'\n```'])
  blocks.append(end)
  insertion=text.index('<RecipeFor')
  p.write_text(text[:insertion]+'\n\n'.join(blocks)+'\n\n'+text[insertion:],encoding='utf-8')
print('Updated eight exact examples in four native guide files.')

---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Loop Factory
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

# Loop Factory

## Parts and first setup

The six parts are the Loop Factory Pattern, Pattern Provider, Handheld Encoder, Pattern Encoding Panel, Network Terminal and Interface Cable. Their recipes appear at the end of this page.

The provider's arrows point toward its independent subnet. Connect the other faces to the main AE network. Only AE power crosses this boundary automatically: the two networks do not share storage or channels. Attach subnet machines directly to the full-block Interface Cable, which connects to AE cables and AE machines without consuming a channel itself. The provider owns its subnet's factory and tags; an ordinary factory network instead needs one Network Terminal. Extra terminals disconnect, and a provider takes priority on its subnet. Recipe-ring and optimizer services also each allow only one owner per network.

Configure a factory pattern's recipe in an ordinary AE2 pattern encoding terminal or the factory Pattern Encoding Panel. The panel can be dyed and switches between the native recipe page and the code page. The handheld encoder edits code only. A Network Terminal accepts only patterns without recipes.


A recipe-free Network Terminal cancels its old execution and starts the current installed program from the beginning whenever its code changes or its factory network receives a redstone rising edge. A held signal does not repeatedly restart it; saving identical code or changing an unsaved draft preserves progress. Replacing or removing its pattern also cancels the previous program. Old WAIT/MUST debt, source declarations, function calls and outgoing pulses are cleared. Physically buffered resources remain in a persistent recovery queue; already delivered resources are not rolled back. Ordinary reloads preserve progress, while a pending code-change restart survives unloading. Recipe-provider orders retain their independent execution. Machine tags can overlap across channels: remove accidental shared bindings when two channels must address different physical inventories.

## Editor and network binding

Right-click air or a non-interactive block with the encoder to open its code page. Sneak-use clears its network binding. An unbound encoder can bind by using a cable, controller or terminal on a valid factory network. Using a provider binds the subnet toward its arrows, rather than its main network.

The title identifies the host. The top-right pattern slot holds the physical factory pattern. The return-arrow button saves code. The panel also has an upload-arrow button and a recipe/worktable page button. Hover for localized descriptions. The code editor is below the title; errors show their line and reason in red. Hover a shortened status to read the full message.

Save writes validated code to the inserted pattern and retains the host draft. A rejected save leaves the last valid physical pattern intact. On the panel, choose a main-network provider to upload the physical pattern into an available slot. Upload rebuilds imports from that provider's subnet tags and validates the generated draft. Missing tags, a full provider or invalid code prevent upload. The displayed draft includes generated imports so error line numbers match. Removing or resetting a pattern is different from unbinding the encoder: sneak-use the pattern itself to clear its recipe and code.

## One tag, many machines

Declare tags with `import A,B`. Names are case-sensitive identifiers using letters, digits and underscores, beginning with a letter or underscore; reserved words are unavailable. Existing bindings survive code edits if the tag remains imported. Removing a tag removes its bindings. Tags live in the terminal, or in the provider for its subnet.

While holding the bound encoder, hold Tab and scroll to select a tag. Use a reachable machine to mark it; Ctrl-use also marks directly connected machines of the same block type. A tag stores a set of positions, so marking the same position again does not duplicate it. Whole-block outlines show tagged machines. No tag can be selected before one is declared. Bulk searches currently visit at most 4096 positions; outlines are limited to 4096 positions within 96 blocks.

`A has minecraft:iron_ingot` sums all reachable machines in A. A transfer quantity is shared by the entire group, not granted again per machine. Full targets are considered when routing across the other group members. Overlapping source and destination labels do not spend quota transferring an endpoint to itself. Stock received during a source-group PUT is not exported again by a later source in that same group operation. Machine traversal is ordered by position; do not rely on an order between different resource types matched by a wildcard.

## Sources, resources and recipes

`get` declares where a later `put` may obtain resources; it does not extract anything. The general factory has no physical inventory buffer. `put` performs direct extraction and insertion. `storage` means accessible storage on the bound AE network. Only provider jobs have `source`: their allocated CPU materials and supported induction-card FE; `put ... into source` returns products toward the main network.

Omit a quantity to remove the limit: `get` and `put` then move everything the selector can reach. With a quantity, the quota applies across matched resources and machines. An explicit face is `on up`, `down`, `north`, `south`, `east` or `west`. Omitting it queries the unsided view first, then uses exposed face handlers. An explicit face queries only that face; machine input/output configuration is never rewritten.

### Selector operands

| Form | Selects | Examples |
| --- | --- | --- |
| Registry ID | one exact resource | `minecraft:iron_ingot` |
| Resource type | every resource of a type | `minecraft::item`, `neoforge::fe` |
| Type and ID | one ID inside one type | `minecraft::item/minecraft:iron_ingot` |
| Recipe material | the nth configured input | `P1`, `P2` |
| Recipe output | the nth product in order | `O1`, `O2` |
| Recipe input set | every material the configured recipe expects | `P` |
| Recipe output set | every product the configured recipe declares | `O` |
| Item tag | every item with that tag | `#minecraft:planks`, `#c:ingots` |
| Wildcards | `*` any run, `?` one character | `minecraft:iron_*`, `minecraft:?ron_ingot` |

A resource type is written with a double colon, so `minecraft::item` selects items and `minecraft::fluid` selects fluids; `neoforge::fe` and `addon::type` follow the same rule, and a single ID can be pinned with `minecraft::item/minecraft:iron_ingot`. Wildcards may match more than one resource, including filler items. Item tags are resolved against the live registry, so they follow datapacks and every loaded mod; a tag that matches nothing simply selects nothing. `#minecraft:stone_crafting_materials` and `#minecraft:logs` are useful vanilla examples.
Item tags are matched through the tag registry of the resource's own type, so item tags, fluid tags and tags contributed by mods or datapacks all work as soon as they are loaded; an unknown tag simply selects nothing.


### Logical operators

| Operator | Meaning | Examples |
| --- | --- | --- |
| `&` | merge: union of both sides | `P1&O1`, `item&fluid` |
| `!` | subtract the right side | `item!gold` |
| `( ... )` | grouping | `(A&B)!(C&D)` |
| `,` in `!( ... )` | exclusion list | `item!(stone,dirt)` |

Merge and subtraction fold strictly left to right, so `A&B!C&D` means `((A∪B)\C)∪D`, and therefore `(A&B)!(C&D)` is the same set as `A&B!C!D` while `A!(B,C)` is the same set as `A!B!C`. Subtraction only removes resources and never adds them. An exclusion may itself be a full expression: `*!(minecraft::fluid,minecraft:*!(minecraft:iron_ingot))` keeps iron ingots while dropping every other fluid. Selectors are accepted wherever a resource is named: `get`, `put`, `has`, `must` quantities and exclusion lists.
Machine tags aggregate with the same operator: `get ... from A&B` and `put ... into A&B` address the union of both machine groups, and `redstone A&B 1 tick` pulses every member of that union once.


### Statements

| Statement | Form |
| --- | --- |
| Import | `import A,B` |
| Name | `name "Factory name"` |
| Declare source | `get ... from ...` |
| Transfer | `put ... into ...` |
| Wait | `wait number tick` |
| Redstone | `redstone Tag number tick` |
| Condition | `if condition do` |
| Loop | `while condition do` |
| Function | `func Name` ... `end` |
| Channel | `channel` |

| Channel | `channel` |

| Stop | `done` |
| Break | `break` |

A declaration reads `get [must] [quantity] selector from tag, source or storage [on face]`, and a transfer reads `put [must] [quantity] selector into tag, source or storage [on face]`. `source` is the provider's CPU allocation and induction cache, `storage` is the bound network's accessible storage, and any other name must be imported first. An explicit face is `on up`, `down`, `north`, `south`, `east` or `west`; omitting it queries the unsided view first and then exposed face handlers. Time units are `tick`, `s`, `min`; one second is 20 ticks and one minute is 1200 ticks. Statements may use at most one `must`, and an `else` accepts an indented block or one instruction on the same line.

Indentation uses spaces, never tabs. Tag and function names accept any Unicode letter, so `存储` or `熔炉组` work exactly like an ASCII name; the first character still has to be a letter or underscore. Programs are limited to 65536 characters and recursion to 64 call frames. Tags, function names and the `name` value cannot reuse reserved words: `import`, `name`, `get`, `from`, `put`, `into`, `on`, `has`, `if`, `else`, `do`, `while`, `wait`, `redstone`, `func`, `end`, `done`, `must`, `storage`, `source`, `true`, `false`, `tick`, `s`, `min`. Invalid code reports its line and reason instead of guessing.

### Recipe references and amounts

`Pn` refers to the nth recipe material, starting at P1, and requires a configured recipe. `On` selects the nth output product in recipe order: O1 is the first/primary output, O2 the second, and so on. Output references work in GET, PUT, HAS and exclusions, and compose with must quantities and every logical operator. They select resource identity, not an automatic recipe-sized quantity. Missing recipes, zero or invalid indexes and indexes beyond the output list are errors; Pn/On cannot be tag or function names. For example, `get must 2 O1 from Furnace` followed by `put O1 into source` waits for two primary products and returns them. Item counts and all other amounts use their registered storage type's native units and signed 64-bit integer limits. Machine transport uses NeoForge transactional item, fluid and FE handlers. Additional standard handlers exposing registered resource identities can be discovered without AE2 storage keys; resources without a registered identity require an explicit adapter.

`P` and `O` are the complete recipe sets. `P` covers every material the invocation allocated, so `get P from source` declares the whole expected input set and `put P into Furnace` moves all of it in one step; `O` covers every product the recipe declares, so `while Furnace has O < 1 do` waits for the product and `put O into source` returns the complete expected output set. Both compose with `&`, `!`, parentheses and `must` quantities exactly like a single material or product, and both require a recipe-bound pattern: a recipe-free program fails to compile with `P and O require a recipe-bound pattern` instead of silently matching nothing. They are reserved words, so they cannot be tag names, function names or the `name` value.

Example: declare up to 64 iron ingots across Input and route them to Output in the same tick:

```text
import Input,Output
get 64 minecraft:iron_ingot from Input
put 64 minecraft:iron_ingot into Output
done
```

For a furnace recipe, allocate its input from `source`, send it to the tagged furnace, explicitly wait for processing, and return the actual output through `source`. In blocking mode, the next recipe batch is admitted only after the current program has completely ended and its input/output and expected products have been settled. Returning the primary output alone does not unlock it while the code tail is still running or waiting. Byproducts, partial returns and unrelated main-network inventory also do not unlock it.

An AppliedFlux induction card enables up to 1000000 FE in the provider source. Removing the card returns unused FE to the main network. This optional addon is not a mod dependency.

Without a must obligation, a fully blocked transfer skips immediately. `put 64 ...` transfers up to 64 and continues even when zero resources are accepted. `put must 64 ...` instead accumulates exactly 64 before continuing. Completed quantities and the current instruction survive saves, so retries do not duplicate earlier transfers. `get must 64 ...` still only declares a source and passes its full-quantity requirement to the following output. Each independent task owns its own continuation: one waiting furnace task does not stall other admitted tasks. Normal ready operations can share a tick; inventory waits are allowed to span ticks.

```text
import Furnace
get must 64 minecraft:stone from Furnace on down
put minecraft:stone into storage
done
```

Use ordinary quantities for best-effort batches and must quantities where the next step requires the complete amount. Explicit wait/redstone still governs deliberately continuous recipe-free loops.

## Control flow and tick timing

Ordinary instructions execute sequentially in the current tick. Only `wait number tick|s|min` and `redstone Tag number tick|s|min` deliberately suspend the program; both require a positive duration. One second is 20 ticks and one minute is 1200 ticks. Redstone is sent to every member of the tag, and execution resumes after its duration. There is no implicit per-instruction delay.

`if condition do` and `while condition do` own the following indented block. Use spaces, not tabs. `else` belongs to its matching if and accepts an indented block or one instruction on the same line. `has` returns a signed 64-bit count; zero is false and nonzero is true. Comparisons include `<`, `>`, `<=` and `>=`.

### Boolean and comparison operators

| Operator | Meaning | Example |
| --- | --- | --- |
| `and` | both sides non-zero | `A has iron > 0 and B has gold = 0` |
| `or` | either side non-zero | `A has iron > 0 or A has gold > 0` |
| `not` | invert the next boolean | `not A has iron > 0` |
| `<` `>` `<=` `>=` `=` | compare two counts | `Tag has stone >= 64` |
| `( ... )` | group a boolean | `(A has iron > 0 or A has gold > 0) and B has stone = 0` |
| `true` / `false` | literal booleans | `true`, `false` |

Write them inside a condition, for example `if Input has minecraft:iron_ingot > 0 and Output has minecraft:gold_ingot = 0 do`. `has` returns a signed 64-bit count and every comparison returns a boolean: zero is false and any non-zero value is true. Conditions nest with parentheses and are combined in the written order. A recipe program still cannot contain an unconditional `while true`; that form is only legal for a recipe-free program whose every repeating path reaches a positive wait or redstone operation.
`has resource in Tag` reads one machine group, while a bare `has resource` totals every machine tag the program imported. Both accept an aggregated group such as `A&B`, and a machine listed in several of those tags is still counted once.



Recipe patterns must finish one round: unconditional loops such as `while true` are forbidden even when their body waits. Recipe jobs retain a finite lifetime instruction budget. Persistent loops are legal only without a recipe. Each repeating execution path must actually reach a positive wait or redstone operation. A wait hidden in an untaken branch, an uncalled function, or outside an inner endless loop does not protect a zero-tick loop. Zero-progress cycles and same-tick watchdog exhaustion stop with an error; instructions are not silently deferred or skipped. There is no lifetime instruction limit on a yielding recipe-free program.
A `channel` block is laid out like every other block: the header has no `do` and no closing keyword, and the body is simply indented. The example in the guide shows a channel and group 0 moving the same resource without sharing a declaration.

A `channel` block is laid out like every other block: the header has no `do` and no closing keyword, and the body is simply indented. The example in the guide shows a channel and group 0 moving the same resource without sharing a declaration.


`break` leaves the innermost loop and continues after it. Written outside every loop, including at the top level of a program, it stops the program exactly like `done`. `break` is reserved and cannot be used as a tag or function name.

```text
import Input,Output
while true do
    get 64 minecraft:iron_ingot from Input
    put 64 minecraft:iron_ingot into Output
    wait 1 tick
```

Use `func work` followed by an indented body and a matching `end`; invoke it by writing `work`. Declarations may appear before or after callers, in branches, loops or other functions. They are program-scoped and do not execute their bodies merely by being declared. Names must be unique and not reserved. Recursion is limited to 64 call frames. `done` stops the whole program, including when used inside a function.

```text
work
done
func work
    wait 1 tick
end
```

## Terminal execution and SFM syntax

An ordinary terminal program starts on a rising redstone signal. A held signal is not a stream of repeated triggers. Unfinished execution, waits and bindings are saved with their owning block. Persistent programs belong on a recipe-free terminal. Recipe programs must complete one round.

Supported SFM programs use EVERY timers/ticks/seconds, global offsets or redstone pulses, INPUT/OUTPUT, FORGET, absolute sides, quantities, quoted labels, exclusions and Boolean conditions. For example:

```text
EVERY 20 TICKS DO
    INPUT 64 minecraft:iron_ingot FROM "Input"
    OUTPUT 64 minecraft:iron_ingot TO "Output"
END
```

SFM timers are scheduled automatically on a terminal. This is a supported subset, not full SFM language compatibility. RETAIN, EACH, WITH/WITHOUT, slot ranges, round-robin and relative sides currently report errors. Do not assume an unsupported clause is ignored.

## Troubleshooting

Check network ownership, power, provider arrow direction, pattern binding and tag membership first. Check that the resource selector excludes filler items and that Pn is valid for the configured recipe. For an unavailable machine, verify its storage interface and optional addon integration. Invalid code must be corrected using the displayed line and reason; runtime recursion or zero-tick loops stop instead of silently continuing. Code is limited to 65536 characters and names to 128. Long drafts and tag bindings persist, but the acceptance report documents the exact tested addon and recovery scope.

{/* factory-examples:start */}

## Runnable examples

Configure recipe mode and tags before pasting. Tag names and recipe order are part of each example. Empty transfers wait; they are not silently skipped.


### Move a filtered batch

Recipe-free terminal. Bind Input and Output to inventories. Put 64 iron and some gold in Input. One rising edge moves up to 64 non-gold items; gold stays. Ordinary requests finish after partial or zero success.

```text
import Input,Output
get 64 minecraft::item!(minecraft:gold_ingot) from Input
put minecraft::item into Output
done
```

### Wait for an exact batch

Recipe-free terminal. Input contains 64 iron. Output initially has room for only 24. After the four-tick wait, 24 move and the job remains on PUT. Free room for 40 more: the job then finishes at exactly 64, including after save/reload.

```text
import Input,Output
get must 64 minecraft:iron_ingot from Input
wait 4 tick
put minecraft:iron_ingot into Output
done
```

### Continuous nested dispatch

Recipe-free terminal only. Input, Buffer and Output are three separate inventories; Ready is a reachable machine receiving a one-tick signal. Each iteration transfers one iron through Buffer to Output. Empty input yields every tick. Supply more iron later to continue. Trigger once: each new rising edge starts another independent run. Replacing a custom pattern does not cancel an already admitted run; disconnect network power to pause it.

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

### Finite nested dispatch

Recipe-free terminal. Put up to 128 iron in Input, leave Buffer and Output empty, and bind Ready. The forward-declared function waits, routes through Buffer and pulses Ready. It exits when Input is empty or Output reaches 128. A conditional recipe loop must actually terminate; a literal while true is forbidden on recipes.

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

### Two-stage recipe: cobblestone to smooth stone

Recipe provider. Configure one cobblestone input (P1) and one smooth-stone output (O1). Bind StageOne and StageTwo to two separate furnace groups and supply fuel externally. Flow: source -> StageOne -> stone -> StageTwo -> O1 -> source. Each blocked stage waits. Blocking admission remains closed through the final two-tick code tail.

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

### Two inputs and two outputs

Recipe provider. Configure P1 = one raw iron, P2 = one raw gold, O1 = one iron ingot, O2 = one gold ingot in that order. Bind IronFurnace and GoldFurnace to separate fueled groups. Gold is returned first deliberately; it must not release blocking before iron and the code tail finish. Output references identify resources, not automatically their recipe amounts.

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

### SFM timer plus redstone

Recipe-free terminal. Input has iron and gold; Output is empty. The timer moves one iron every two ticks while available. Each rising edge moves one gold; a held signal does not repeat. Clauses inside one run execute in order: a blocked clause retains that run, not a parallel branch. Guards prevent this example from waiting on an empty input.

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

### SFM finite recipe

Recipe provider. Configure one cobblestone -> one stone, bind Furnace and add external fuel. INPUT declares the assigned P1, OUTPUT feeds the top, FORGET clears the old declarations, and O1 is extracted from the bottom and returned. The recipe run ends when assigned resources and outputs are settled; it is not an endless standalone timer.

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

If a `GET`/`PUT` moves nothing and the UI shows no error, check the selector first: resource types need a double colon (`minecraft::item`, `minecraft::fluid`). A single colon is read as a registry ID (`minecraft:oak_log`), so it matches no resource and is skipped silently.

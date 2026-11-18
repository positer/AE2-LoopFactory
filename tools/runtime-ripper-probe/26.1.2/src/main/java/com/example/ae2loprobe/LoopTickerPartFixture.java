package com.example.ae2loprobe;

import appeng.api.parts.IPartHost;
import appeng.core.definitions.AEBlocks;
import com.example.ae2lightoptimizer.part.LoopTickerPart;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Acceptance fixture for the loop ticker part: it mounts the part on a cable bus and checks that the
 * block the part points at gets accelerated compared to an untouched furnace.
 */
public final class LoopTickerPartFixture {
    public static final boolean ENABLED = "loop_ticker_part".equals(System.getProperty("ae2lo.probe.chain"));
    private static final int WAIT_TICKS = 400;
    private static boolean started;
    private static boolean finished;
    private static boolean passed;
    private static int waited;
    private static Level level;
    static final String MENU_FRAME = "ae2lo-ripper-loop-ticker-part-menu.png";
    private static LoopTickerPart lastPart;
    private static final Map<Direction, BlockPos> blockFurnaces = new java.util.EnumMap<>(Direction.class);
    private record Station(BlockPos pos, Direction face, boolean part) {}
    private static final java.util.List<Station> stations = new java.util.ArrayList<>();

    private static void addStation(BlockPos pos, Direction face, boolean part) {
        if (part) {
            level.setBlockAndUpdate(pos, AEBlocks.CABLE_BUS.block().defaultBlockState());
            IPartHost host = (IPartHost) level.getBlockEntity(pos);
            host.addPart(appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT), face, null);
            host.addPart(com.example.ae2lightoptimizer.item.ModItems.LOOP_TICKER_PART.get(), face, null);
        } else {
            var machine = com.example.ae2lightoptimizer.block.ModBlocks.LOOP_TICKER.get();
            level.setBlockAndUpdate(pos, machine.getOrientationStrategy().setFacing(machine.defaultBlockState(), face));
        }
        for (Direction side : Direction.values()) placeFurnace(pos.relative(side));
        level.setBlockAndUpdate(pos.above(3), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        stations.add(new Station(pos, face, part));
    }

    private static void powerStations() {
        for (Station station : stations) {
            var machine = level.getBlockEntity(station.pos());
            var node = station.part()
                    ? ((LoopTickerPart)((IPartHost)machine).getPart(station.face())).getGridNode()
                    : ((com.example.ae2lightoptimizer.block.LoopTickerBlockEntity)machine).getMainNode().getNode();
            var battery = (appeng.api.networking.IInWorldGridNodeHost)level.getBlockEntity(station.pos().above(3));
            appeng.api.networking.GridHelper.createConnection(battery.getGridNode(Direction.DOWN), node);
        }
    }

    private static boolean checkStations(int control) {
        boolean passed = true;
        for (Station station : stations) {
            Map<String, Integer> yields = new LinkedHashMap<>();
            for (Direction side : Direction.values()) {
                int amount = count(station.pos().relative(side));
                yields.put(side.getName(), amount);
                passed &= side == station.face() ? amount > control : amount == control;
            }
            summary.put((station.part() ? "part_" : "block_") + station.face().getName(), yields);
        }
        return passed;
    }
    private static BlockPos controlFurnace;
    private static BlockPos fastFurnace;
    private static final Map<String, Object> summary = new LinkedHashMap<>();

    private LoopTickerPartFixture() {
    }

    public static void start(Level value, BlockPos base) {
        if (started) return;
        started = true;
        level = value;
        // Control furnace and an identical one that the part will accelerate.
        controlFurnace = base;

        placeFurnace(controlFurnace);

        // A dedicated controllerless island powers both forms.
        BlockPos cablePos = base.east(4).below();
        level.setBlockAndUpdate(cablePos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cablePos, AEBlocks.CABLE_BUS.block().defaultBlockState());
        if (!(level.getBlockEntity(cablePos) instanceof IPartHost host)) {
            fail("cable bus did not create a part host");
            return;
        }
        host.addPart(appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT),
                Direction.EAST, null);
        Direction mountSide = Direction.UP;
        BlockPos targetPos = cablePos.relative(mountSide);
        level.setBlockAndUpdate(targetPos, Blocks.FURNACE.defaultBlockState());
        if (level.getBlockEntity(targetPos) instanceof AbstractFurnaceBlockEntity fed) {
            fed.setItem(0, new ItemStack(Items.RAW_IRON, 16));
            fed.setItem(1, new ItemStack(Items.COAL, 8));
        }
        fastFurnace = targetPos;
        var part = host.addPart(com.example.ae2lightoptimizer.item.ModItems.LOOP_TICKER_PART.get(),
                mountSide, null);
        if (!(part instanceof LoopTickerPart ticker)) {
            fail("part was not installed");
            return;
        }
        ticker.setRate(4);
        summary.put("partInstalled", true);
        summary.put("partClass", part.getClass().getSimpleName());
        summary.put("partSide", String.valueOf(part.getSide()));
        lastPart = ticker;
        var machine = com.example.ae2lightoptimizer.block.ModBlocks.LOOP_TICKER.get();
        for (Direction face : Direction.values()) {
            var blockPos = base.offset(4 * (face.ordinal() % 3), 4, 4 + 4 * (face.ordinal() / 3));
            var state = machine.getOrientationStrategy().setFacing(machine.defaultBlockState(), face);
            level.setBlockAndUpdate(blockPos, state);
            if (!net.minecraft.world.level.block.Block.isShapeFullBlock(state.getCollisionShape(level, blockPos))) {
                fail("incomplete collision shape: " + face);
                return;
            }
            level.setBlockAndUpdate(blockPos.relative(face.getOpposite()),
                    AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            var target = blockPos.relative(face);
            placeFurnace(target);
            blockFurnaces.put(face, target);
            var testPos = base.offset(-12 + 5 * (face.ordinal() % 3), 10, 5 * (face.ordinal() / 3));
            addStation(testPos, face, false);
            addStation(testPos.north(12), face, true);
        }

    }

    private static void placeFurnace(BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, 16));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));
        }
    }

    private static int phase;
    private static int isolatedOutput = -1;

    public static void tick() {
        if (!started || finished) return;
        ++waited;
        if (phase == 0 && waited == 20) powerStations();
        if (waited < WAIT_TICKS) return;
        if (phase == 0) {
            int plain = count(controlFurnace);
            int fast = count(fastFurnace);
            summary.put("onlyFacingAccelerated", checkStations(plain));
            var server = (net.minecraft.server.level.ServerLevel) level;
            var player = server.players().getFirst();
            var cable = fastFurnace.below();
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    new net.minecraft.world.phys.Vec3(cable.getX() + .75, cable.getY() + .8, cable.getZ() + .5),
                    Direction.EAST, cable, false);
            player.connection.teleport(cable.getX() + 2.5, cable.getY() + 1, cable.getZ() + .5, 90, 20);
            level.getBlockState(cable).useWithoutItem(level, player, hit);
            summary.put("partMenuOpened", player.containerMenu instanceof com.example.ae2lightoptimizer.menu.LoopTickerMenu
                    && player.containerMenu.getType() == com.example.ae2lightoptimizer.menu.LoopTickerMenu.PART_TYPE.get());
            var grid = lastPart.getMainNode().getGrid();
            summary.put("controllerState", grid == null ? "missing" : grid.getPathingService().getControllerState().name());
            Map<String, Integer> outputs = new LinkedHashMap<>();
            blockFurnaces.forEach((face, pos) -> outputs.put(face.getName(), count(pos)));
            summary.put("blockOutputs", outputs);
            summary.put("allBlockFacesAccelerated", outputs.size() == 6
                    && outputs.values().stream().allMatch(amount -> amount > plain));
            if (lastPart != null) {

                summary.put("nodeActiveAtEnd", lastPart.isActive());
            }
            summary.put("controlOutput", plain);
            summary.put("acceleratedOutput", fast);
            // Phase 1: the same part on an isolated, unpowered cable must not accelerate.
            BlockPos isolated = new BlockPos(20, 100, 20);
            level.setBlockAndUpdate(isolated, AEBlocks.CABLE_BUS.block().defaultBlockState());
            if (level.getBlockEntity(isolated) instanceof IPartHost isolatedHost) {
                BlockPos furnacePos = isolated.above();
                level.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
                if (level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity fed) {
                    fed.setItem(0, new ItemStack(Items.RAW_IRON, 16));
                    fed.setItem(1, new ItemStack(Items.COAL, 8));
                }
                isolatedHost.addPart(com.example.ae2lightoptimizer.item.ModItems.LOOP_TICKER_PART.get(),
                        Direction.UP, null);
                if (isolatedHost.getPart(Direction.UP) instanceof LoopTickerPart unpowered) {
                    unpowered.setRate(4);
                }
                isolatedFurnace = furnacePos;
                phase = 1;
                waited = 0;
            } else {
                passed = false;
                finished = true;
                summary.put("failure", "isolated cable bus missing");
            }
            return;
        }
        finished = true;
        isolatedOutput = count(isolatedFurnace);
        summary.put("isolatedOutput", isolatedOutput);
        int plain = count(controlFurnace);
        summary.put("controlOutputAfterSecondPhase", plain);
        passed = ((int) summary.get("acceleratedOutput")) > ((int) summary.get("controlOutput"))
                && Boolean.TRUE.equals(summary.get("partMenuOpened"))
                && "NO_CONTROLLER".equals(summary.get("controllerState"))
                && Boolean.TRUE.equals(summary.get("allBlockFacesAccelerated"))
                && Boolean.TRUE.equals(summary.get("onlyFacingAccelerated"))
                && isolatedOutput >= 0 && isolatedOutput <= (int) summary.get("controlOutput") + 1;
    }

    private static BlockPos isolatedFurnace;

    private static int count(BlockPos pos) {
        if (level != null && level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            return furnace.getItem(2).getCount();
        }
        return -1;
    }

    private static void fail(String reason) {
        finished = true;
        passed = false;
        summary.put("failure", reason);
    }

    public static boolean isFinished() {
        return finished;
    }

    public static boolean passed() {
        return passed;
    }

    public static Map<String, Object> summary() {
        return summary;
    }
}
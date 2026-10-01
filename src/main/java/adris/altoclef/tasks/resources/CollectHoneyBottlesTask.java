package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.GetCloseToBlockTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public final class CollectHoneyBottlesTask extends ResourceTask {
    private final int count;
    private final Task searchTask = new SearchChunkForBlockTask(Blocks.BEE_NEST, Blocks.BEEHIVE);
    private BlockPos hive;
    private Task interactTask;

    public CollectHoneyBottlesTask(int count) {
        super(Items.HONEY_BOTTLE, count);
        this.count = count;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        hive = null;
        interactTask = null;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        int honeyBottles = mod.getItemStorage().getItemCount(Items.HONEY_BOTTLE);
        int bottlesNeeded = count - honeyBottles;
        if (bottlesNeeded <= 0) return null;

        int emptyBottles = mod.getItemStorage().getItemCount(Items.GLASS_BOTTLE);
        if (emptyBottles < bottlesNeeded) {
            setDebugState("Getting glass bottles for honey");
            return new CataloguedResourceTask(
                    new ItemTarget(Items.GLASS_BOTTLE, bottlesNeeded - emptyBottles));
        }

        if (hive == null || !isHive(mod, hive)) {
            Optional<BlockPos> foundHive = mod.getBlockScanner().getNearestBlock(
                    Blocks.BEE_NEST, Blocks.BEEHIVE);
            if (foundHive.isEmpty()) {
                setDebugState("Searching for a bee nest");
                return searchTask;
            }
            hive = foundHive.get();
            interactTask = null;
        }

        if (!hasCampfire(mod, hive)) {
            if (!mod.getItemStorage().hasItemInventoryOnly(Items.CAMPFIRE)) {
                setDebugState("Getting a campfire to calm bees");
                return new CataloguedResourceTask(new ItemTarget(Items.CAMPFIRE, 1));
            }
            BlockPos campfirePos = hive.down(2);
            if (!WorldHelper.isSolidBlock(campfirePos.down())) {
                setDebugState("Finding a safe bee nest with solid ground below");
                hive = null;
                return searchTask;
            }
            setDebugState("Placing a campfire under the bee nest");
            return new PlaceBlockTask(campfirePos, Blocks.CAMPFIRE);
        }

        if (mod.getWorld().getBlockState(hive).get(Properties.HONEY_LEVEL) < 5) {
            if (!hive.isWithinDistance(mod.getPlayer().getPos(), 20)) {
                setDebugState("Approaching a bee nest");
                return new GetCloseToBlockTask(hive);
            }
            setDebugState("Waiting for bees to refill the nest");
            return null;
        }

        if (interactTask == null) interactTask = new InteractWithBlockTask(Items.GLASS_BOTTLE, hive);
        setDebugState("Collecting honey bottles");
        return interactTask;
    }

    private boolean isHive(AltoClef mod, BlockPos pos) {
        Block block = mod.getWorld().getBlockState(pos).getBlock();
        return block == Blocks.BEE_NEST || block == Blocks.BEEHIVE;
    }

    private boolean hasCampfire(AltoClef mod, BlockPos pos) {
        for (BlockPos check : WorldHelper.scanRegion(pos.down(6), pos.down())) {
            if (mod.getWorld().getBlockState(check).isOf(Blocks.CAMPFIRE)) return true;
        }
        return false;
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectHoneyBottlesTask task && task.count == count;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + count + " honey bottles";
    }
}

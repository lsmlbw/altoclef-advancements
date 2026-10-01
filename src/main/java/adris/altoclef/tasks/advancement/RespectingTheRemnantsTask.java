package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.BrushableBlockEntityAccessor;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BrushableBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class RespectingTheRemnantsTask extends Task {
    private static final Item[] POTTERY_SHERDS = {
            Items.ANGLER_POTTERY_SHERD, Items.ARCHER_POTTERY_SHERD, Items.ARMS_UP_POTTERY_SHERD,
            Items.BLADE_POTTERY_SHERD, Items.BREWER_POTTERY_SHERD, Items.BURN_POTTERY_SHERD,
            Items.DANGER_POTTERY_SHERD, Items.EXPLORER_POTTERY_SHERD, Items.FLOW_POTTERY_SHERD,
            Items.FRIEND_POTTERY_SHERD, Items.GUSTER_POTTERY_SHERD, Items.HEART_POTTERY_SHERD,
            Items.HEARTBREAK_POTTERY_SHERD, Items.HOWL_POTTERY_SHERD, Items.MINER_POTTERY_SHERD,
            Items.MOURNER_POTTERY_SHERD, Items.PLENTY_POTTERY_SHERD, Items.PRIZE_POTTERY_SHERD,
            Items.SCRAPE_POTTERY_SHERD, Items.SHEAF_POTTERY_SHERD, Items.SHELTER_POTTERY_SHERD,
            Items.SKULL_POTTERY_SHERD, Items.SNORT_POTTERY_SHERD
    };
    private static final Set<String> NATURAL_LOOT_TABLES = Set.of(
            "archaeology/desert_pyramid",
            "archaeology/desert_well",
            "archaeology/trail_ruins_rare",
            "archaeology/trail_ruins_common",
            "archaeology/ocean_ruin_warm",
            "archaeology/ocean_ruin_cold"
    );

    private final Task searchTask = new SearchChunkForBlockTask(
            Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL);
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame brushTimer = new TimerGame(20);
    private final Set<BlockPos> attemptedBlocks = new HashSet<>();
    private BlockPos target;
    private Block baseBlock;
    private boolean brushing;
    private boolean brushedNaturalBlock;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        attemptedBlocks.clear();
        target = null;
        baseBlock = null;
        brushing = false;
        brushedNaturalBlock = false;
        finished = false;
        successful = false;
        releaseBrush();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (brushedNaturalBlock && mod.getItemStorage().hasItem(POTTERY_SHERDS)) {
            releaseBrush();
            successful = true;
            finished = true;
            return null;
        }

        if (!mod.getItemStorage().hasItem(Items.BRUSH)) {
            releaseBrush();
            if (!TaskCatalogue.taskExists(Items.BRUSH)) {
                mod.logWarning("Cannot complete Respecting the Remnants: the brush resource task is unavailable.");
                finished = true;
                return null;
            }
            setDebugState("Crafting a brush");
            return TaskCatalogue.getItemTask(Items.BRUSH, 1);
        }

        if (target == null || !isUntouchedNaturalBlock(mod, target)) {
            releaseBrush();
            target = findUntouchedNaturalBlock(mod);
            if (target == null) {
                setDebugState("Searching for naturally generated suspicious sand or gravel");
                return searchTask;
            }
            baseBlock = mod.getWorld().getBlockState(target).isOf(Blocks.SUSPICIOUS_SAND)
                    ? Blocks.SAND : Blocks.GRAVEL;
        }

        if (mod.getPlayer().squaredDistanceTo(target.toCenterPos()) > 4.5 * 4.5) {
            releaseBrush();
            setDebugState("Approaching untouched suspicious archaeology");
            return new GetToBlockTask(target);
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.BRUSH)) {
            releaseBrush();
            return null;
        }

        var rotation = LookHelper.getLookRotation(mod, target.toCenterPos());
        LookHelper.lookAt(mod, target.toCenterPos(), false);
        if (!LookHelper.isLookingAt(mod, rotation)
                || !(MinecraftClient.getInstance().crosshairTarget instanceof BlockHitResult hit)
                || !hit.getBlockPos().equals(target)) {
            releaseBrush();
            setDebugState("Aiming the brush at untouched suspicious archaeology");
            return null;
        }

        if (!brushing) {
            brushing = true;
            brushedNaturalBlock = true;
            brushTimer.reset();
        }
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        if (mod.getWorld().getBlockState(target).isOf(baseBlock)) {
            attemptedBlocks.add(target);
            target = null;
            brushing = false;
            releaseBrush();
            return null;
        }
        if (brushTimer.elapsed()) {
            attemptedBlocks.add(target);
            target = null;
            brushing = false;
            releaseBrush();
            setDebugState("The suspicious block contained no sherd; searching for another");
            return null;
        }
        setDebugState("Brushing untouched suspicious archaeology");
        return null;
    }

    private BlockPos findUntouchedNaturalBlock(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL)
                .stream()
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> !attemptedBlocks.contains(pos))
                .filter(pos -> isUntouchedNaturalBlock(mod, pos))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                .orElse(null);
    }

    private boolean isUntouchedNaturalBlock(AltoClef mod, BlockPos pos) {
        Block stateBlock = mod.getWorld().getBlockState(pos).getBlock();
        if (stateBlock != Blocks.SUSPICIOUS_SAND && stateBlock != Blocks.SUSPICIOUS_GRAVEL) return false;
        if (!(mod.getWorld().getBlockEntity(pos) instanceof BrushableBlockEntity blockEntity)) return false;
        RegistryKey<LootTable> lootTable =
                ((BrushableBlockEntityAccessor) blockEntity).altoclef$getLootTable();
        return lootTable != null && NATURAL_LOOT_TABLES.contains(lootTable.getValue().getPath());
    }

    private void releaseBrush() {
        AltoClef.getInstance().getInputControls().release(Input.CLICK_RIGHT);
        if (AltoClef.getInstance().getPlayer() != null) {
            AltoClef.getInstance().getPlayer().stopUsingItem();
        }
    }

    public boolean wasSuccessful() {
        return successful;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
        releaseBrush();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof RespectingTheRemnantsTask;
    }

    @Override
    protected String toDebugString() {
        return "Brushing naturally generated suspicious blocks for a pottery sherd";
    }
}

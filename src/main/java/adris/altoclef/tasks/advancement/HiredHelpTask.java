package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public final class HiredHelpTask extends Task {
    private static final int SEARCH_RADIUS = 6;
    private static final int GOLEM_CONFIRM_TIMEOUT_SECONDS = 5;

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final Set<IronGolemEntity> existingGolems = new HashSet<>();
    private BlockPos basePosition;
    private PlaceBlockTask pumpkinPlacementTask;
    private int golemWaitTicks;
    private boolean materialsReady;
    private boolean pumpkinPlaced;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        existingGolems.clear();
        basePosition = null;
        pumpkinPlacementTask = null;
        golemWaitTicks = 0;
        materialsReady = false;
        pumpkinPlaced = false;
        finished = false;
        successful = false;
        for (IronGolemEntity golem : AltoClef.getInstance()
                .getEntityTracker().getTrackedEntities(IronGolemEntity.class)) {
            if (golem.isAlive()) existingGolems.add(golem);
        }
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        IronGolemEntity summonedGolem = findSummonedGolem(mod);
        if (summonedGolem != null) {
            successful = true;
            finished = true;
            return null;
        }

        if (!materialsReady) {
            if (mod.getItemStorage().getItemCount(Items.IRON_BLOCK) < 4) {
                if (!TaskCatalogue.taskExists(Items.IRON_BLOCK)) {
                    return fail(mod, "no iron block resource task is available");
                }
                setDebugState("Obtaining four iron blocks for the golem");
                return TaskCatalogue.getItemTask(Items.IRON_BLOCK, 4);
            }

            if (!mod.getItemStorage().hasItem(Items.CARVED_PUMPKIN)) {
                if (!TaskCatalogue.taskExists(Items.CARVED_PUMPKIN)) {
                    return fail(mod, "no carved pumpkin resource task is available");
                }
                setDebugState("Obtaining a carved pumpkin");
                return TaskCatalogue.getItemTask(Items.CARVED_PUMPKIN, 1);
            }
            materialsReady = true;
        }

        if (basePosition == null) {
            basePosition = findBuildPosition(mod);
            if (basePosition == null) {
                setDebugState("Finding a clear, level place to build the iron golem");
                return exploreTask;
            }
            pumpkinPlacementTask = null;
            pumpkinPlaced = false;
            golemWaitTicks = 0;
        }

        BlockPos[] requiredAirPositions = {
                basePosition.west(), basePosition.east(),
                basePosition.up(2).west(), basePosition.up(2).east(),
                basePosition.up(3)
        };
        for (BlockPos position : requiredAirPositions) {
            if (!mod.getWorld().getBlockState(position).isAir()) {
                setDebugState("Clearing space for the iron golem at " + position.toShortString());
                return new DestroyBlockTask(position);
            }
        }

        if (!mod.getWorld().getBlockState(basePosition).isOf(Blocks.IRON_BLOCK)) {
            setDebugState("Placing the bottom iron block");
            return new PlaceBlockTask(basePosition, Blocks.IRON_BLOCK);
        }
        if (!mod.getWorld().getBlockState(basePosition.up()).isOf(Blocks.IRON_BLOCK)) {
            setDebugState("Placing the center iron block");
            return new PlaceBlockTask(basePosition.up(), Blocks.IRON_BLOCK);
        }
        if (!mod.getWorld().getBlockState(basePosition.up().west()).isOf(Blocks.IRON_BLOCK)) {
            setDebugState("Placing the west arm of the iron golem");
            return new PlaceBlockTask(basePosition.up().west(), Blocks.IRON_BLOCK);
        }
        if (!mod.getWorld().getBlockState(basePosition.up().east()).isOf(Blocks.IRON_BLOCK)) {
            setDebugState("Placing the east arm of the iron golem");
            return new PlaceBlockTask(basePosition.up().east(), Blocks.IRON_BLOCK);
        }

        BlockPos pumpkinPosition = basePosition.up(2);
        if (!pumpkinPlaced) {
            if (pumpkinPlacementTask == null) {
                setDebugState("Placing the carved pumpkin to summon the iron golem");
                pumpkinPlacementTask = new PlaceBlockTask(pumpkinPosition, Blocks.CARVED_PUMPKIN);
            }
            if (!pumpkinPlacementTask.isFinished()) return pumpkinPlacementTask;
            pumpkinPlaced = true;
            golemWaitTicks = 0;
        }

        if (pumpkinPlaced) {
            if (golemWaitTicks++ >= GOLEM_CONFIRM_TIMEOUT_SECONDS * 20) {
                return fail(mod, "the iron golem did not spawn after the pumpkin was placed");
            }
            setDebugState("Waiting for the summoned iron golem to appear");
            return null;
        }
        return null;
    }

    private BlockPos findBuildPosition(AltoClef mod) {
        BlockPos player = mod.getPlayer().getBlockPos();
        for (int y = -1; y <= 1; y++) {
            for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        BlockPos candidate = player.add(dx, y, dz);
                        if (!WorldHelper.isSolidBlock(candidate.down())) continue;
                        if (requiredSpaceIsClear(mod, candidate)) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean requiredSpaceIsClear(AltoClef mod, BlockPos base) {
        BlockPos[] positions = {
                base, base.up(), base.up().east(), base.up().west(), base.up(2),
                base.east(), base.west(), base.up(2).east(), base.up(2).west(), base.up(3)
        };
        for (BlockPos position : positions) {
            if (!mod.getWorld().getBlockState(position).isAir()) return false;
        }
        return true;
    }

    private IronGolemEntity findSummonedGolem(AltoClef mod) {
        if (basePosition == null) return null;
        return mod.getEntityTracker().getTrackedEntities(IronGolemEntity.class).stream()
                .filter(IronGolemEntity::isAlive)
                .filter(golem -> !existingGolems.contains(golem))
                .filter(golem -> golem.getBlockPos().isWithinDistance(basePosition, 4))
                .findFirst()
                .orElse(null);
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Hired Help: " + reason + ".");
        finished = true;
        return null;
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
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof HiredHelpTask;
    }

    @Override
    protected String toDebugString() {
        return "Summoning an iron golem for Hired Help";
    }
}

package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.util.math.BlockPos;

public final class ItSpreadsTask extends Task {
    private static final double CATALYST_RANGE = 8;
    private static final double CATALYST_RANGE_SQUARED = CATALYST_RANGE * CATALYST_RANGE;

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private LivingEntity target;
    private BlockPos catalystPosition;
    private BlockPos pendingCatalystPosition;
    private Task activeTask;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        catalystPosition = null;
        pendingCatalystPosition = null;
        activeTask = null;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (target != null && target.getHealth() <= 0) {
            successful = true;
            finished = true;
            return null;
        }
        if (target != null && !target.isAlive()) {
            target = null;
            activeTask = null;
        }

        if (catalystPosition == null
                || !mod.getWorld().getBlockState(catalystPosition)
                .isOf(adris.altoclef.multiversion.versionedfields.Blocks.SCULK_CATALYST)) {
            catalystPosition = findCatalyst(mod);
        }

        if (target == null) {
            target = findTarget(mod, catalystPosition);
        }

        if (target == null) {
            if (!mod.getItemStorage().hasItem(adris.altoclef.multiversion.versionedfields.Items.SCULK_CATALYST)) {
                if (!TaskCatalogue.taskExists(
                        adris.altoclef.multiversion.versionedfields.Items.SCULK_CATALYST)) {
                    mod.logWarning("Cannot complete It Spreads: no sculk catalyst resource task is available.");
                    finished = true;
                    return null;
                }
                setDebugState("Obtaining a sculk catalyst");
                return TaskCatalogue.getItemTask(
                        adris.altoclef.multiversion.versionedfields.Items.SCULK_CATALYST, 1);
            }
            setDebugState("Searching for an experience-dropping mob");
            return exploreTask;
        }

        if (catalystPosition != null && isNearCatalyst(target, catalystPosition)) {
            setDebugState("Killing an experience-dropping mob near the sculk catalyst");
            activeTask = new KillEntityTask(target);
            return activeTask;
        }

        if (pendingCatalystPosition != null) {
            if (mod.getWorld().getBlockState(pendingCatalystPosition)
                    .isOf(adris.altoclef.multiversion.versionedfields.Blocks.SCULK_CATALYST)) {
                catalystPosition = pendingCatalystPosition;
                pendingCatalystPosition = null;
                activeTask = null;
            } else {
                setDebugState("Placing a sculk catalyst near the mob");
                return activeTask;
            }
        }

        if (!mod.getItemStorage().hasItem(
                adris.altoclef.multiversion.versionedfields.Items.SCULK_CATALYST)) {
            if (!TaskCatalogue.taskExists(
                    adris.altoclef.multiversion.versionedfields.Items.SCULK_CATALYST)) {
                mod.logWarning("Cannot complete It Spreads: no sculk catalyst resource task is available.");
                finished = true;
                return null;
            }
            setDebugState("Collecting a sculk catalyst to place beside a mob");
            return TaskCatalogue.getItemTask(
                    adris.altoclef.multiversion.versionedfields.Items.SCULK_CATALYST, 1);
        }

        if (mod.getPlayer().squaredDistanceTo(target) > 16 * 16) {
            setDebugState("Approaching an experience-dropping mob");
            return new GetToEntityTask(target, 8);
        }

        BlockPos placePosition = findCatalystPlacement(mod, target);
        if (placePosition == null) {
            setDebugState("Finding clear ground within eight blocks of the mob");
            return exploreTask;
        }
        if (!mod.getWorld().getBlockState(placePosition)
                .isOf(adris.altoclef.multiversion.versionedfields.Blocks.SCULK_CATALYST)) {
            setDebugState("Placing a sculk catalyst near the mob");
            pendingCatalystPosition = placePosition;
            activeTask = new PlaceBlockTask(placePosition,
                    adris.altoclef.multiversion.versionedfields.Blocks.SCULK_CATALYST);
            return activeTask;
        }
        catalystPosition = placePosition;
        setDebugState("Killing an experience-dropping mob near the sculk catalyst");
        activeTask = new KillEntityTask(target);
        return activeTask;
    }

    private BlockPos findCatalyst(AltoClef mod) {
        return mod.getBlockScanner()
                .getNearestBlock(adris.altoclef.multiversion.versionedfields.Blocks.SCULK_CATALYST)
                .filter(pos -> mod.getWorld().getBlockState(pos)
                        .isOf(adris.altoclef.multiversion.versionedfields.Blocks.SCULK_CATALYST))
                .orElse(null);
    }

    private LivingEntity findTarget(AltoClef mod, BlockPos catalyst) {
        LivingEntity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity living)
                    || !living.isAlive()
                    || !isExperienceMob(living)
                    || (catalyst != null && !isNearCatalyst(living, catalyst)
                    && !mod.getItemStorage().hasItem(
                    adris.altoclef.multiversion.versionedfields.Items.SCULK_CATALYST))) {
                continue;
            }
            double distance = living.squaredDistanceTo(mod.getPlayer());
            if (distance < closestDistance) {
                closest = living;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private boolean isExperienceMob(LivingEntity entity) {
        if (entity instanceof EnderDragonEntity || entity instanceof WardenEntity) return false;
        if (entity instanceof Monster) return true;
        return entity instanceof AnimalEntity animal && !animal.isBaby();
    }

    private boolean isNearCatalyst(LivingEntity entity, BlockPos catalyst) {
        return entity.squaredDistanceTo(catalyst.toCenterPos()) <= CATALYST_RANGE_SQUARED;
    }

    private BlockPos findCatalystPlacement(AltoClef mod, LivingEntity entity) {
        BlockPos center = entity.getBlockPos();
        BlockPos best = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos candidate = center.add(dx, 0, dz);
                if (candidate.equals(center)
                        || !mod.getWorld().getBlockState(candidate).isReplaceable()
                        || !WorldHelper.isSolidBlock(candidate.down())
                        || candidate.toCenterPos().squaredDistanceTo(entity.getPos()) > CATALYST_RANGE_SQUARED) {
                    continue;
                }
                double distance = candidate.getSquaredDistance(mod.getPlayer().getBlockPos());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best;
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
        return other instanceof ItSpreadsTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing an experience-dropping mob near a sculk catalyst";
    }
}

package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasks.speedrun.beatgame.EndGatewayTask;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.Optional;

public final class GreatViewTask extends Task {
    private static final double REQUIRED_VERTICAL_DISTANCE = 50;
    private static final double SHULKER_APPROACH_DISTANCE = 8;
    private static final Block[] END_CITY_BLOCKS = {
            Blocks.PURPUR_BLOCK,
            Blocks.PURPUR_PILLAR,
            Blocks.PURPUR_STAIRS,
            Blocks.PURPUR_SLAB,
            Blocks.END_ROD
    };
    private final EndGatewayTask outerIslandGatewayTask = new EndGatewayTask(false);
    private final EndGatewayTask centralIslandGatewayTask = new EndGatewayTask(false);
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private ShulkerEntity currentShulker;
    private Task approachTask;
    private double previousLevitationY;
    private double levitationDistance;
    private double highestY;
    private double lowestY;
    private boolean trackingLevitation;
    private boolean advancementAchieved;
    private boolean returningToCenter;
    private boolean finished;

    @Override
    protected void onStart() {
        currentShulker = null;
        approachTask = null;
        trackingLevitation = false;
        levitationDistance = 0;
        advancementAchieved = false;
        returningToCenter = false;
        finished = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;
        if (WorldHelper.getCurrentDimension() != Dimension.END) {
            finished = true;
            return null;
        }

        if (!outerIslandGatewayTask.isFinished()) {
            setDebugState("Traveling through an End gateway to find shulkers");
            return outerIslandGatewayTask;
        }
        if (!outerIslandGatewayTask.wasSuccessful()) {
            mod.logWarning("Cannot complete Great View From Up Here: unable to reach an outer End island through a gateway.");
            finished = true;
            return null;
        }

        double currentY = mod.getPlayer().getY();
        if (mod.getPlayer().hasStatusEffect(StatusEffects.LEVITATION)) {
            if (!trackingLevitation) {
                previousLevitationY = currentY;
                highestY = currentY;
                lowestY = currentY;
                trackingLevitation = true;
            } else {
                levitationDistance += Math.abs(currentY - previousLevitationY);
                previousLevitationY = currentY;
                highestY = Math.max(highestY, currentY);
                lowestY = Math.min(lowestY, currentY);
            }
            if (levitationDistance >= REQUIRED_VERTICAL_DISTANCE
                    || Math.max(highestY - lowestY, 0) >= REQUIRED_VERTICAL_DISTANCE) {
                advancementAchieved = true;
                returningToCenter = true;
            }
            if (!returningToCenter) {
                setDebugState("Traveling vertically under Levitation (" + Math.round(
                        Math.max(levitationDistance, highestY - lowestY)) + "/50 blocks)");
                return null;
            }
        } else {
            trackingLevitation = false;
        }

        if (returningToCenter) {
            if (!centralIslandGatewayTask.isFinished()) {
                setDebugState("Returning to the central End island after levitating 50 blocks");
                return centralIslandGatewayTask;
            }
            finished = true;
            return null;
        }

        Optional<ShulkerEntity> shulker = mod.getEntityTracker()
                .getClosestEntity(mod.getPlayer().getPos(),
                        entity -> entity.isAlive(),
                        ShulkerEntity.class)
                .map(entity -> (ShulkerEntity) entity);
        if (shulker.isPresent()) {
            currentShulker = shulker.get();
            if (!currentShulker.isInRange(mod.getPlayer(), SHULKER_APPROACH_DISTANCE)) {
                approachTask = new GetToEntityTask(currentShulker, SHULKER_APPROACH_DISTANCE);
                setDebugState("Approaching a shulker in the End City");
                return approachTask;
            }
            setDebugState("Waiting for a shulker projectile to grant Levitation");
            return null;
        }

        BlockPos cityBlock = findNearbyCityBlock(mod).orElse(null);
        if (cityBlock != null && !cityBlock.isWithinDistance(mod.getPlayer().getBlockPos(), 12)) {
            setDebugState("Approaching End City shulkers");
            return new adris.altoclef.tasks.movement.GetToBlockTask(cityBlock);
        }

        setDebugState("Searching the outer End islands for a shulker");
        return exploreTask;
    }

    private Optional<BlockPos> findNearbyCityBlock(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(END_CITY_BLOCKS).stream()
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> isCityBlock(mod.getWorld().getBlockState(pos).getBlock()))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())));
    }

    private boolean isCityBlock(Block block) {
        for (Block cityBlock : END_CITY_BLOCKS) {
            if (block == cityBlock) return true;
        }
        return false;
    }

    public boolean wasSuccessful() {
        return advancementAchieved;
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
        return other instanceof GreatViewTask;
    }

    @Override
    protected String toDebugString() {
        return "Traveling 50 blocks under Levitation";
    }
}

package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasks.speedrun.beatgame.EndGatewayTask;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class EndCityTask extends Task {
    private static final double CITY_APPROACH_DISTANCE = 12;
    private static final Block[] CITY_BLOCKS = {
            Blocks.PURPUR_BLOCK,
            Blocks.PURPUR_PILLAR,
            Blocks.PURPUR_STAIRS,
            Blocks.PURPUR_SLAB,
            Blocks.END_ROD
    };
    private final DefaultGoToDimensionTask goToEndTask = new DefaultGoToDimensionTask(Dimension.END);
    private final EndGatewayTask enterGatewayTask = new EndGatewayTask(false);
    private final TimeoutWanderTask searchTask = new TimeoutWanderTask(true);
    private EndGatewayTask returnGatewayTask;
    private Task approachTask;
    private BlockPos cityBlock;
    private boolean cityFound;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        approachTask = null;
        cityBlock = null;
        returnGatewayTask = null;
        cityFound = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (cityFound) {
            if (returnGatewayTask == null) returnGatewayTask = new EndGatewayTask(false);
            if (!returnGatewayTask.isFinished()) {
                setDebugState("Returning to the central End island through its gateway");
                return returnGatewayTask;
            }
            successful = true;
            finished = true;
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.END) {
            setDebugState("Returning to the End to search for an End City");
            return goToEndTask;
        }

        if (!enterGatewayTask.isFinished()) {
            setDebugState("Traveling through an End gateway to the outer islands");
            return enterGatewayTask;
        }
        if (!enterGatewayTask.wasSuccessful()) {
            mod.logWarning("Cannot search for The City at the End of the Game: unable to reach an outer End island through a gateway.");
            finished = true;
            return null;
        }

        if (cityBlock != null && !isCityBlock(mod.getWorld().getBlockState(cityBlock).getBlock())) {
            cityBlock = null;
            approachTask = null;
        }
        if (cityBlock == null) {
            cityBlock = findNearbyCityBlock(mod).orElse(null);
        }

        Optional<ShulkerEntity> shulker = mod.getEntityTracker()
                .getClosestEntity(mod.getPlayer().getPos(), ShulkerEntity.class)
                .map(entity -> (ShulkerEntity) entity);
        if (shulker.isPresent() && shulker.get().squaredDistanceTo(mod.getPlayer()) <= 16 * 16) {
            cityFound = true;
            setDebugState("Reached an End City");
            return null;
        }

        if (cityBlock != null) {
            if (cityBlock.isWithinDistance(mod.getPlayer().getBlockPos(), CITY_APPROACH_DISTANCE)) {
                cityFound = true;
                setDebugState("Reached an End City");
                return null;
            }
            if (approachTask == null) approachTask = new GetToBlockTask(cityBlock);
            setDebugState("Approaching an End City");
            return approachTask;
        }

        setDebugState("Searching the outer End islands for an End City");
        return searchTask;
    }

    private Optional<BlockPos> findNearbyCityBlock(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(CITY_BLOCKS).stream()
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> isCityBlock(mod.getWorld().getBlockState(pos).getBlock()))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())));
    }

    private boolean isCityBlock(Block block) {
        for (Block cityBlock : CITY_BLOCKS) {
            if (block == cityBlock) return true;
        }
        return false;
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
        return other instanceof EndCityTask;
    }

    @Override
    protected String toDebugString() {
        return "Finding an End City";
    }
}

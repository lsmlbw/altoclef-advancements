package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.compound.ConstructNetherPortalBucketTask;
import adris.altoclef.tasks.construction.compound.ConstructNetherPortalObsidianTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Some generic tasks require us to go to the nether/overworld/end.
 * <p>
 * The user should be able to specify how this should be done in settings
 * (ex, craft a new portal from scratch or check particular portal areas first or highway or whatever)
 */
public class DefaultGoToDimensionTask extends Task {

    private final Dimension _target;
    // Cached to keep build properties alive if this task pauses/resumes.
    private final Task _cachedNetherBucketConstructionTask = new ConstructNetherPortalBucketTask();
    private final GoToStrongholdPortalTask _cachedStrongholdTask = new GoToStrongholdPortalTask(12);

    public DefaultGoToDimensionTask(Dimension target) {
        _target = target;
    }

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        if (WorldHelper.getCurrentDimension() == _target) return null;

        switch (_target) {
            case OVERWORLD:
                switch (WorldHelper.getCurrentDimension()) {
                    case NETHER:
                        return goToOverworldFromNetherTask();
                    case END:
                        return goToOverworldFromEndTask();
                }
                break;
            case NETHER:
                switch (WorldHelper.getCurrentDimension()) {
                    case OVERWORLD:
                        return goToNetherFromOverworldTask();
                    case END:
                        // First go to the overworld
                        return goToOverworldFromEndTask();
                }
                break;
            case END:
                switch (WorldHelper.getCurrentDimension()) {
                    case NETHER:
                        // First go to the overworld
                        return goToOverworldFromNetherTask();
                    case OVERWORLD:
                        return goToEndTask();
                }
                break;
        }

        setDebugState(WorldHelper.getCurrentDimension() + " -> " + _target + " is NOT IMPLEMENTED YET!");
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof DefaultGoToDimensionTask task) {
            return task._target == _target;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Going to dimension: " + _target + " (default version)";
    }

    @Override
    public boolean isFinished() {
        return WorldHelper.getCurrentDimension() == _target;
    }

    private Task goToOverworldFromNetherTask() {
        AltoClef mod = AltoClef.getInstance();

        if (netherPortalIsClose(mod)) {
            setDebugState("Going to nether portal");
            return new EnterNetherPortalTask(Dimension.NETHER);
        }

        Optional<BlockPos> closest = mod.getMiscBlockTracker().getLastUsedNetherPortal(Dimension.NETHER);
        if (closest.isPresent()) {
            setDebugState("Going to last nether portal pos");
            return new GetToBlockTask(closest.get());
        }

        setDebugState("Constructing nether portal with obsidian");
        return new ConstructNetherPortalObsidianTask();
    }

    private Task goToOverworldFromEndTask() {
        setDebugState("TODO: Go to center portal (at 0,0). If it doesn't exist, kill ender dragon lol");
        return null;
    }

    private Task goToNetherFromOverworldTask() {
        AltoClef mod = AltoClef.getInstance();

        if (netherPortalIsClose(mod)) {
            setDebugState("Going to nether portal");
            return new EnterNetherPortalTask(Dimension.NETHER);
        }
        return switch (mod.getModSettings().getOverworldToNetherBehaviour()) {
            case BUILD_PORTAL_VANILLA -> _cachedNetherBucketConstructionTask;
            case GO_TO_HOME_BASE -> new GetToBlockTask(mod.getModSettings().getHomeBasePosition());
        };
    }

    private Task goToEndTask() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getBlockScanner().anyFound(Blocks.END_PORTAL)) {
            mod.getExtraBaritoneSettings().canWalkOnEndPortal(true);
            setDebugState("Entering the End portal");
            return new DoToClosestBlockTask(blockPos -> new GetToBlockTask(blockPos.up()), Blocks.END_PORTAL);
        }

        List<BlockPos> frames = mod.getBlockScanner().getKnownLocations(Blocks.END_PORTAL_FRAME);
        Optional<BlockPos> closestFrame = frames.stream()
                .min((left, right) -> Double.compare(
                        left.getSquaredDistance(mod.getPlayer().getBlockPos()),
                        right.getSquaredDistance(mod.getPlayer().getBlockPos())));
        if (closestFrame.isPresent()) {
            BlockPos portalFrame = closestFrame.get();
            frames = frames.stream().filter(pos -> pos.isWithinDistance(portalFrame, 20)).toList();
        }
        long filledFrames = frames.stream()
                .filter(mod.getChunkTracker()::isChunkLoaded)
                .filter(pos -> mod.getWorld().getBlockState(pos).isOf(Blocks.END_PORTAL_FRAME))
                .filter(pos -> mod.getWorld().getBlockState(pos).get(EndPortalFrameBlock.EYE))
                .count();
        if (frames.size() >= 12 && filledFrames < 12) {
            int eyesNeeded = 12 - (int) filledFrames;
            if (mod.getItemStorage().getItemCount(Items.ENDER_EYE) < eyesNeeded) {
                setDebugState("Collecting eyes to activate the End portal");
                return TaskCatalogue.getItemTask(Items.ENDER_EYE, eyesNeeded);
            }
            setDebugState("Activating the End portal");
            return new DoToClosestBlockTask(
                    pos -> new InteractWithBlockTask(Items.ENDER_EYE, pos),
                    pos -> mod.getChunkTracker().isChunkLoaded(pos)
                            && mod.getWorld().getBlockState(pos).isOf(Blocks.END_PORTAL_FRAME)
                            && !mod.getWorld().getBlockState(pos).get(EndPortalFrameBlock.EYE),
                    Blocks.END_PORTAL_FRAME);
        }

        setDebugState("Locating the End portal in the stronghold");
        return _cachedStrongholdTask;
    }

    private boolean netherPortalIsClose(AltoClef mod) {
        if (mod.getBlockScanner().anyFound(Blocks.NETHER_PORTAL)) {
            Optional<BlockPos> closest = mod.getBlockScanner().getNearestBlock( Blocks.NETHER_PORTAL);
            return closest.isPresent() && closest.get().isWithinDistance(mod.getPlayer().getPos(), 2000);
        }
        return false;
    }

    public enum OVERWORLD_TO_NETHER_BEHAVIOUR {
        BUILD_PORTAL_VANILLA,
        GO_TO_HOME_BASE
    }
}

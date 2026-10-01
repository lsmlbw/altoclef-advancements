package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import baritone.api.utils.input.Input;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class StickySituationTask extends Task {
    private BlockPos honeyPos;
    private BlockPos standPos;
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        honeyPos = null;
        standPos = null;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            releaseMovement(mod);
            setDebugState("Returning to the Overworld to find honey");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (honeyPos == null || !mod.getWorld().getBlockState(honeyPos).isOf(Blocks.HONEY_BLOCK)) {
            honeyPos = mod.getBlockScanner().getNearestBlock(Blocks.HONEY_BLOCK).orElse(null);
        }
        if (honeyPos == null) {
            if (!mod.getItemStorage().hasItem(Items.HONEY_BLOCK)) {
                if (!TaskCatalogue.taskExists(Items.HONEY_BLOCK)) {
                    mod.logWarning("Cannot complete Sticky Situation: honey block recipe is unavailable.");
                    finished = true;
                    return null;
                }
                setDebugState("Obtaining honey bottles and crafting a honey block");
                return TaskCatalogue.getItemTask(Items.HONEY_BLOCK, 1);
            }
            if (!choosePlacementPosition(mod)) {
                setDebugState("Finding a clear, level spot for a honey block");
                return exploreTask;
            }
            setDebugState("Placing the honey block for a controlled fall");
            return new PlaceBlockTask(honeyPos, Blocks.HONEY_BLOCK);
        }

        if (standPos == null && !chooseAdjacentStandPosition(mod)) {
            setDebugState("Finding a clear side of the honey block");
            return exploreTask;
        }
        if (!mod.getPlayer().getBlockPos().isWithinDistance(standPos, 1.5)) {
            releaseMovement(mod);
            setDebugState("Moving beside the honey block");
            return new GetToBlockTask(standPos);
        }

        Vec3d blockCenter = Vec3d.ofCenter(honeyPos);
        var targetRotation = LookHelper.getLookRotation(mod, blockCenter);
        LookHelper.lookAt(mod, blockCenter, false);
        if (!LookHelper.isLookingAt(mod, targetRotation)) {
            releaseMovement(mod);
            setDebugState("Aiming at the honey block");
            return null;
        }
        var velocity = mod.getPlayer().getVelocity();
        if (mod.getPlayer().horizontalCollision && !mod.getPlayer().isOnGround()
                && velocity.y < 0 && velocity.y > -0.15) {
            successful = true;
            finished = true;
            releaseMovement(mod);
            return null;
        }

        mod.getInputControls().hold(Input.MOVE_FORWARD);
        if (mod.getPlayer().isOnGround()) {
            mod.getInputControls().hold(Input.JUMP);
        } else {
            mod.getInputControls().release(Input.JUMP);
        }
        setDebugState("Jumping against the honey block while falling");
        return null;
    }

    private boolean choosePlacementPosition(AltoClef mod) {
        BlockPos playerPos = mod.getPlayer().getBlockPos();
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos candidate = playerPos.offset(side);
            if (mod.getWorld().getBlockState(candidate).isAir()
                    && mod.getWorld().getBlockState(candidate.up()).isAir()
                    && WorldHelper.isSolidBlock(candidate.down())) {
                honeyPos = candidate;
                standPos = playerPos;
                return true;
            }
        }
        return false;
    }

    private boolean chooseAdjacentStandPosition(AltoClef mod) {
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos candidate = honeyPos.offset(side);
            if (mod.getWorld().getBlockState(candidate).isAir()
                    && mod.getWorld().getBlockState(candidate.up()).isAir()
                    && WorldHelper.isSolidBlock(candidate.down())) {
                standPos = candidate;
                return true;
            }
        }
        return false;
    }

    private void releaseMovement(AltoClef mod) {
        mod.getInputControls().release(Input.JUMP);
        mod.getInputControls().release(Input.MOVE_FORWARD);
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
        releaseMovement(AltoClef.getInstance());
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StickySituationTask;
    }

    @Override
    protected String toDebugString() {
        return "Slowing a fall on a honey block";
    }
}

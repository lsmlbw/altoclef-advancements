package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PostmortalTask extends Task {
    private static final int DROP_HEIGHT = 128;
    private static final int FALL_TIMEOUT_SECONDS = 15;

    private final TimerGame fallTimer = new TimerGame(FALL_TIMEOUT_SECONDS);
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private BlockPos towerBase;
    private BlockPos exitPosition;
    private int towerIndex;
    private int totemCountBeforeFall;
    private boolean falling;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        towerBase = null;
        exitPosition = null;
        towerIndex = 0;
        totemCountBeforeFall = 0;
        falling = false;
        finished = false;
        successful = false;
        releaseMovement();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (falling) return monitorFall(mod);

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Returning to the Overworld for a controlled totem activation");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!mod.getItemStorage().hasItem(Items.TOTEM_OF_UNDYING)) {
            if (!TaskCatalogue.taskExists(Items.TOTEM_OF_UNDYING)) {
                return fail(mod, "no totem of undying resource task is available");
            }
            setDebugState("Obtaining a totem of undying from an evoker");
            return TaskCatalogue.getItemTask(Items.TOTEM_OF_UNDYING, 1);
        }

        if (!storageHasOffhandTotem()) {
            setDebugState("Equipping the totem in the offhand");
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(Items.TOTEM_OF_UNDYING, 1), PlayerSlot.OFFHAND_SLOT);
        }

        if (towerBase == null) {
            towerBase = findTowerBase(mod);
            if (towerBase == null) {
                setDebugState("Finding clear, solid ground with room for a protected fall");
                return exploreTask;
            }
            exitPosition = mod.getPlayer().getBlockPos();
            towerIndex = 0;
        }

        if (mod.getItemStorage().getItemCount(Items.SCAFFOLDING) < DROP_HEIGHT) {
            if (!TaskCatalogue.taskExists(Items.SCAFFOLDING)) {
                return fail(mod, "no scaffolding resource task is available");
            }
            setDebugState("Obtaining scaffolding for the controlled fatal fall");
            return TaskCatalogue.getItemTask(Items.SCAFFOLDING, DROP_HEIGHT);
        }

        int topY = towerBase.getY() + DROP_HEIGHT + 1;
        if (topY >= mod.getWorld().getTopY() - 4) {
            towerBase = null;
            setDebugState("The selected position has insufficient build height; choosing another site");
            return null;
        }

        if (towerIndex < DROP_HEIGHT) {
            BlockPos currentTop = towerBase.up(towerIndex);
            if (!mod.getPlayer().getBlockPos().equals(currentTop)) {
                setDebugState("Climbing the scaffold to extend the protected fall tower");
                return new GetToBlockTask(currentTop);
            }
            BlockPos scaffoldPosition = towerBase.up(towerIndex + 1);
            if (mod.getWorld().getBlockState(scaffoldPosition).isOf(net.minecraft.block.Blocks.SCAFFOLDING)) {
                towerIndex++;
                return null;
            }
            if (!mod.getWorld().getBlockState(scaffoldPosition).isAir()) {
                towerBase = null;
                setDebugState("The scaffold column is obstructed; choosing another site");
                return null;
            }
            setDebugState("Building a 128-block protected fall tower (" + towerIndex + "/" + DROP_HEIGHT + ")");
            return new PlaceBlockTask(scaffoldPosition, net.minecraft.block.Blocks.SCAFFOLDING);
        }

        BlockPos topPosition = towerBase.up(DROP_HEIGHT + 1);
        if (!mod.getPlayer().getBlockPos().equals(topPosition)) {
            setDebugState("Climbing the scaffold tower while the totem is equipped");
            return new GetToBlockTask(topPosition);
        }

        if (horizontalDistance(mod.getPlayer().getPos(), towerBase.toCenterPos()) < 1.35) {
            Vec3d aim = new Vec3d(exitPosition.getX() + 0.5, mod.getPlayer().getY(),
                    exitPosition.getZ() + 0.5);
            var lookRotation = LookHelper.getLookRotation(mod, aim);
            LookHelper.lookAt(mod, aim, false);
            if (!LookHelper.isLookingAt(mod, lookRotation)) {
                setDebugState("Aiming away from the scaffold to start the fall");
                return null;
            }
            mod.getInputControls().hold(Input.MOVE_FORWARD);
            mod.getInputControls().hold(Input.JUMP);
            setDebugState("Stepping off the scaffold toward the safe landing area");
            return null;
        }
        releaseMovement();
        releaseMovement();
        totemCountBeforeFall = mod.getItemStorage().getItemCount(Items.TOTEM_OF_UNDYING);
        falling = true;
        fallTimer.reset();
        setDebugState("Falling with the totem equipped");
        return null;
    }

    private double horizontalDistance(Vec3d first, Vec3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean storageHasOffhandTotem() {
        return adris.altoclef.util.helpers.StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT)
                .isOf(Items.TOTEM_OF_UNDYING);
    }

    private BlockPos findTowerBase(AltoClef mod) {
        BlockPos playerPos = mod.getPlayer().getBlockPos();
        if (playerPos.getY() + DROP_HEIGHT >= mod.getWorld().getTopY() - 4) return null;
        for (int radius = 1; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos candidate = playerPos.add(dx, 0, dz);
                    if (!mod.getWorld().getBlockState(candidate).isAir()
                            || !WorldHelper.isSolidBlock(candidate.down())) {
                        continue;
                    }
                    boolean clearColumn = true;
                    for (int y = 1; y <= DROP_HEIGHT + 1; y++) {
                        if (!mod.getWorld().getBlockState(candidate.up(y)).isAir()) {
                            clearColumn = false;
                            break;
                        }
                    }
                    Block landingBlock = mod.getWorld().getBlockState(candidate.down()).getBlock();
                    boolean reducesFallDamage = landingBlock == Blocks.SLIME_BLOCK
                            || landingBlock == Blocks.HAY_BLOCK
                            || landingBlock == Blocks.HONEY_BLOCK
                            || landingBlock instanceof net.minecraft.block.BedBlock;
                    if (clearColumn && !reducesFallDamage) return candidate;
                }
            }
        }
        return null;
    }

    private Task monitorFall(AltoClef mod) {
        if (!mod.getPlayer().isAlive()) {
            return fail(mod, "the player died before the totem could activate");
        }
        if (mod.getItemStorage().getItemCount(Items.TOTEM_OF_UNDYING) < totemCountBeforeFall
                && mod.getPlayer().getHealth() > 0) {
            successful = true;
            finished = true;
            releaseMovement();
            return null;
        }
        if (mod.getPlayer().getY() <= towerBase.getY()
                && (mod.getPlayer().isOnGround() || mod.getPlayer().isTouchingWater())) {
            return fail(mod, "the fall did not activate the totem");
        }
        if (fallTimer.elapsed()) {
            return fail(mod, "the controlled fall did not reach a safe landing before timing out");
        }
        setDebugState("Verifying totem activation during the fatal fall");
        return null;
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Postmortal: " + reason + ".");
        releaseMovement();
        finished = true;
        return null;
    }

    private void releaseMovement() {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.MOVE_FORWARD);
        mod.getInputControls().release(Input.JUMP);
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
        releaseMovement();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PostmortalTask;
    }

    @Override
    protected String toDebugString() {
        return "Using a totem of undying to survive fatal damage";
    }
}

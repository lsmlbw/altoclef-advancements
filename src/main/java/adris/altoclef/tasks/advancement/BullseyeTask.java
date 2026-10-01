package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class BullseyeTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/bullseye");
    private static final double MIN_SHOT_DISTANCE = 30;
    private static final double FIRING_DISTANCE = 34;
    private static final double[] AIM_HEIGHTS = {1.5, 2.5, 3.5, 4.5, 5.5, 6.5};

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame drawTimer = new TimerGame(1.2);
    private final TimerGame hitTimer = new TimerGame(4);
    private PlaceBlockNearbyTask placeTargetTask;
    private Task movementTask;
    private BlockPos targetPos;
    private BlockPos firingPos;
    private boolean drawing;
    private boolean waitingForHit;
    private int aimIndex;
    private int shotCount;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        placeTargetTask = null;
        movementTask = null;
        targetPos = null;
        firingPos = null;
        drawing = false;
        waitingForHit = false;
        aimIndex = 0;
        shotCount = 0;
        finished = false;
        successful = false;
        releaseBow();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Bullseye advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            releaseBow();
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            releaseBow();
            setDebugState("Returning to the Overworld to complete Bullseye");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!mod.getItemStorage().hasItem(Items.TARGET)) {
            releaseBow();
            if (!TaskCatalogue.taskExists(Items.TARGET)) {
                return fail(mod, "the target block recipe is unavailable");
            }
            setDebugState("Obtaining a target block for Bullseye");
            return TaskCatalogue.getItemTask(Items.TARGET, 1);
        }

        if (targetPos == null || !mod.getWorld().getBlockState(targetPos).isOf(Blocks.TARGET)) {
            targetPos = mod.getBlockScanner().getNearestBlock(Blocks.TARGET).orElse(null);
            if (targetPos == null) {
                if (placeTargetTask == null) {
                    placeTargetTask = new PlaceBlockNearbyTask(Blocks.TARGET);
                }
                if (!placeTargetTask.isFinished()) {
                    releaseBow();
                    setDebugState("Placing a target block");
                    return placeTargetTask;
                }
                targetPos = placeTargetTask.getPlaced();
                placeTargetTask = null;
                if (targetPos == null) {
                    return fail(mod, "the target block placement did not provide a position");
                }
            }
        }

        if (!mod.getItemStorage().hasItem(Items.BOW)) {
            releaseBow();
            return getResource(mod, Items.BOW, "Obtaining a bow for Bullseye");
        }
        if (!hasArrow(mod)) {
            releaseBow();
            return getResource(mod, Items.ARROW, "Obtaining arrows for Bullseye");
        }

        if (waitingForHit) {
            if (!hitTimer.elapsed()) {
                setDebugState("Waiting for the arrow to hit the target block");
                return null;
            }
            waitingForHit = false;
            drawing = false;
            shotCount++;
            aimIndex = (aimIndex + 1) % AIM_HEIGHTS.length;
        }

        if (firingPos == null
                || horizontalDistance(mod.getPlayer().getPos(), targetPos.toCenterPos()) < MIN_SHOT_DISTANCE
                || movementTask != null && movementTask.isFinished()) {
            firingPos = calculateFiringPosition(mod, targetPos);
            movementTask = new GetToBlockTask(firingPos);
        }
        if (horizontalDistance(mod.getPlayer().getPos(), targetPos.toCenterPos()) < MIN_SHOT_DISTANCE) {
            releaseBow();
            setDebugState("Moving at least 30 blocks away from the target");
            return movementTask;
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.BOW)) {
            releaseBow();
            return null;
        }

        Vec3d center = targetPos.toCenterPos();
        Vec3d aimPoint = center.add(0, AIM_HEIGHTS[aimIndex], 0);
        baritone.api.utils.Rotation rotation = LookHelper.getLookRotation(mod, aimPoint);
        LookHelper.lookAt(mod, aimPoint, false);
        if (!LookHelper.isLookingAt(mod, rotation)) {
            releaseBow();
            setDebugState("Aiming above the target to compensate for arrow drop");
            return null;
        }

        if (!drawing) {
            drawing = true;
            drawTimer.reset();
        }
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        if (!drawTimer.elapsed()) {
            setDebugState("Fully drawing the bow from at least 30 blocks away");
            return null;
        }
        if (horizontalDistance(mod.getPlayer().getPos(), targetPos.toCenterPos()) < MIN_SHOT_DISTANCE) {
            releaseBow();
            drawing = false;
            return null;
        }

        releaseBow();
        mod.getInputControls().tryPress(Input.CLICK_RIGHT);
        waitingForHit = true;
        hitTimer.reset();
        setDebugState("Firing at the target bullseye from 30+ blocks (" + (shotCount + 1) + " attempts)");
        return null;
    }

    private BlockPos calculateFiringPosition(AltoClef mod, BlockPos target) {
        Vec3d player = mod.getPlayer().getPos();
        Vec3d targetCenter = target.toCenterPos();
        double dx = player.x - targetCenter.x;
        double dz = player.z - targetCenter.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) {
            dx = 1;
            dz = 0;
            length = 1;
        }
        double scale = FIRING_DISTANCE / length;
        return BlockPos.ofFloored(targetCenter.x + dx * scale, player.y, targetCenter.z + dz * scale);
    }

    private double horizontalDistance(Vec3d first, Vec3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean hasArrow(AltoClef mod) {
        return mod.getItemStorage().hasItem(Items.ARROW)
                || mod.getItemStorage().hasItem(Items.SPECTRAL_ARROW)
                || mod.getItemStorage().hasItem(Items.TIPPED_ARROW);
    }

    private Task getResource(AltoClef mod, net.minecraft.item.Item item, String state) {
        if (!TaskCatalogue.taskExists(item)) {
            return fail(mod, "no resource task is available for " + item.getName().getString());
        }
        setDebugState(state);
        return TaskCatalogue.getItemTask(item, item == Items.ARROW ? 16 : 1);
    }

    private AdvancementProgress getAdvancementProgress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return null;
        var manager = client.getNetworkHandler().getAdvancementHandler();
        PlacedAdvancement entry = manager.getManager().get(ADVANCEMENT_ID);
        if (entry == null) return null;
        return ((ClientAdvancementManagerAccessor) manager)
                .altoclef$getAdvancementProgresses().get(entry);
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Bullseye: " + reason + ".");
        finished = true;
        releaseBow();
        return null;
    }

    private void releaseBow() {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.CLICK_RIGHT);
        if (mod.getPlayer() != null) mod.getPlayer().stopUsingItem();
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
        releaseBow();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BullseyeTask;
    }

    @Override
    protected String toDebugString() {
        return "Hitting the center of a target block from at least 30 blocks away";
    }
}

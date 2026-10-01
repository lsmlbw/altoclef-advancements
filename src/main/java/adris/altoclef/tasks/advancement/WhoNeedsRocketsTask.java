package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.multiversion.versionedfields.VersionedFieldHelper;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class WhoNeedsRocketsTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/who_needs_rockets");
    private static final double REQUIRED_RISE = 8;

    private final TimeoutWanderTask findSafeGroundTask = new TimeoutWanderTask(true);
    private final TimerGame launchTimer = new TimerGame(8);
    private boolean launchInProgress;
    private boolean waitingForAdvancement;
    private double launchY;
    private double highestY;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        launchInProgress = false;
        waitingForAdvancement = false;
        launchY = 0;
        highestY = 0;
        finished = false;
        successful = false;
        releaseInputs();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (!VersionedFieldHelper.isSupported(Items.WIND_CHARGE)) {
            return fail(mod, "wind charges are not available in this Minecraft version");
        }

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Who Needs Rockets? advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            releaseInputs();
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            releaseInputs();
            setDebugState("Returning to the Overworld for Who Needs Rockets?");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (launchInProgress) {
            releaseInputs();
            highestY = Math.max(highestY, mod.getPlayer().getY());
            if (highestY >= launchY + REQUIRED_RISE) {
                waitingForAdvancement = true;
                launchTimer.reset();
                setDebugState("Reached eight blocks of upward travel; waiting for advancement confirmation");
                launchInProgress = false;
                return null;
            }
            if (mod.getPlayer().isOnGround() || launchTimer.elapsed()) {
                launchInProgress = false;
                waitingForAdvancement = false;
            } else {
                setDebugState("Tracking the peak height from the wind-charge launch");
                return null;
            }
        }

        if (waitingForAdvancement) {
            if (!launchTimer.elapsed()) {
                setDebugState("Waiting for the wind-charge advancement to register");
                return null;
            }
            waitingForAdvancement = false;
        }

        if (!mod.getItemStorage().hasItem(Items.WIND_CHARGE)) {
            if (!TaskCatalogue.taskExists(Items.WIND_CHARGE)) {
                return fail(mod, "the wind charge resource task is unavailable");
            }
            setDebugState("Obtaining a wind charge for the upward launch");
            return TaskCatalogue.getItemTask(Items.WIND_CHARGE, 1);
        }

        BlockPos feet = mod.getPlayer().getBlockPos();
        if (!mod.getPlayer().isOnGround() || !WorldHelper.isSolidBlock(feet.down())
                || !hasLaunchClearance(mod, feet)) {
            releaseInputs();
            setDebugState("Finding solid ground with clear space above for a wind-charge launch");
            return findSafeGroundTask;
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.WIND_CHARGE)) {
            releaseInputs();
            setDebugState("Equipping a wind charge");
            return null;
        }

        Vec3d aimPoint = Vec3d.ofCenter(feet.down());
        var rotation = LookHelper.getLookRotation(mod, aimPoint);
        LookHelper.lookAt(mod, aimPoint, false);
        if (!LookHelper.isLookingAt(mod, rotation)) {
            releaseInputs();
            setDebugState("Aiming straight down at the block underfoot");
            return null;
        }

        launchY = mod.getPlayer().getY();
        highestY = launchY;
        launchInProgress = true;
        launchTimer.reset();
        mod.getInputControls().tryPress(Input.JUMP);
        mod.getInputControls().tryPress(Input.CLICK_RIGHT);
        setDebugState("Jumping and using a wind charge to launch upward");
        return null;
    }

    private boolean hasLaunchClearance(AltoClef mod, BlockPos feet) {
        for (int offset = 1; offset <= 9; offset++) {
            BlockPos pos = feet.up(offset);
            if (!mod.getWorld().getBlockState(pos).isAir()) return false;
        }
        return true;
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Who Needs Rockets?: " + reason + ".");
        finished = true;
        releaseInputs();
        return null;
    }

    private void releaseInputs() {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.JUMP);
        mod.getInputControls().release(Input.CLICK_RIGHT);
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

    public boolean wasSuccessful() {
        return successful;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
        releaseInputs();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof WhoNeedsRocketsTask;
    }

    @Override
    protected String toDebugString() {
        return "Launching at least eight blocks with a wind charge for Who Needs Rockets?";
    }
}

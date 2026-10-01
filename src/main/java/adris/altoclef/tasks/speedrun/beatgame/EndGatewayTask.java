package adris.altoclef.tasks.speedrun.beatgame;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.ThrowEnderPearlSimpleProjectileTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Optional;

public final class EndGatewayTask extends Task {
    private static final double TELEPORT_DISTANCE = 32;
    private static final int MAX_PEARL_ATTEMPTS = 5;
    private final TimerGame searchTimer = new TimerGame(60);
    private final TimerGame attemptTimer = new TimerGame(120);
    private final boolean returnToCenter;
    private BlockPos gateway;
    private ThrowEnderPearlSimpleProjectileTask pearlTask;
    private int pearlAttempts;
    private boolean attemptedEntry;
    private boolean remoteGetawayAchieved;
    private boolean finished;
    private boolean successful;

    public EndGatewayTask() {
        this(true);
    }

    public EndGatewayTask(boolean returnToCenter) {
        this.returnToCenter = returnToCenter;
    }

    @Override
    protected void onStart() {
        gateway = null;
        pearlTask = null;
        pearlAttempts = 0;
        attemptedEntry = false;
        remoteGetawayAchieved = false;
        finished = false;
        successful = false;
        searchTimer.reset();
        attemptTimer.reset();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;
        if (WorldHelper.getCurrentDimension() != Dimension.END) {
            finished = true;
            return null;
        }

        if (attemptedEntry && isAwayFromGateway(mod)) {
            if (!remoteGetawayAchieved) {
                successful = true;
                remoteGetawayAchieved = true;
                if (!returnToCenter) {
                    finished = true;
                    return null;
                }
                gateway = null;
                pearlTask = null;
                pearlAttempts = 0;
                attemptedEntry = false;
                searchTimer.reset();
                attemptTimer.reset();
                setDebugState("Remote Getaway complete; returning to the central island");
                return null;
            }
            if (WorldHelper.distanceXZ(mod.getPlayer().getPos(), Vec3d.ZERO) < TELEPORT_DISTANCE * 4) {
                finished = true;
            } else {
                gateway = null;
                pearlTask = null;
                pearlAttempts = 0;
                attemptedEntry = false;
                searchTimer.reset();
                attemptTimer.reset();
            }
            return null;
        }

        if (gateway == null) {
            Optional<BlockPos> found = mod.getBlockScanner().getKnownLocations(Blocks.END_GATEWAY).stream()
                    .filter(pos -> mod.getWorld().getBlockState(pos).isOf(Blocks.END_GATEWAY))
                    .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())));
            if (found.isEmpty()) {
                if (searchTimer.elapsed()) {
                    finished = true;
                    return null;
                }
                setDebugState("Waiting for the End gateway to be detected");
                return null;
            }
            gateway = found.get().toImmutable();
        }

        if (attemptTimer.elapsed() || pearlAttempts >= MAX_PEARL_ATTEMPTS) {
            finished = true;
            return null;
        }

        if (pearlTask != null && !pearlTask.isFinished()) {
            setDebugState("Throwing an ender pearl through the End gateway");
            return pearlTask;
        }
        if (pearlTask != null) {
            pearlTask = null;
            if (attemptedEntry && isAwayFromGateway(mod)) {
                if (!remoteGetawayAchieved) {
                    successful = true;
                    remoteGetawayAchieved = true;
                    if (!returnToCenter) {
                        finished = true;
                        return null;
                    }
                    gateway = null;
                    pearlAttempts = 0;
                    attemptedEntry = false;
                    searchTimer.reset();
                    attemptTimer.reset();
                } else if (WorldHelper.distanceXZ(mod.getPlayer().getPos(), Vec3d.ZERO)
                        < TELEPORT_DISTANCE * 4) {
                    finished = true;
                }
                return null;
            }
        }

        if (mod.getPlayer().getPos().squaredDistanceTo(
                new Vec3d(gateway.getX() + 0.5, gateway.getY() + 0.5,
                        gateway.getZ() + 0.5)) > 6 * 6) {
            setDebugState("Approaching the End gateway");
            return new GetToBlockTask(gateway.up());
        }

        if (!mod.getItemStorage().hasItem(Items.ENDER_PEARL)) {
            setDebugState("Collecting an ender pearl to enter the gateway");
            if (!TaskCatalogue.taskExists(Items.ENDER_PEARL)) {
                finished = true;
                return null;
            }
            return TaskCatalogue.getItemTask(Items.ENDER_PEARL, 1);
        }

        pearlTask = new ThrowEnderPearlSimpleProjectileTask(gateway);
        pearlAttempts++;
        attemptedEntry = true;
        setDebugState("Entering the End gateway with an ender pearl");
        return pearlTask;
    }

    private boolean isAwayFromGateway(AltoClef mod) {
        return WorldHelper.distanceXZ(mod.getPlayer().getPos(),
                new Vec3d(gateway.getX() + 0.5, 0, gateway.getZ() + 0.5)) > TELEPORT_DISTANCE;
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
        return other instanceof EndGatewayTask task && task.returnToCenter == returnToCenter;
    }

    @Override
    protected String toDebugString() {
        return "Entering the End gateway";
    }
}

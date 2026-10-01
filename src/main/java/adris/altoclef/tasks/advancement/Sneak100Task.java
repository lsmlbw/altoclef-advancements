package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.Optional;

public final class Sneak100Task extends Task {
    private static final double SENSOR_RANGE = 8;
    private static final double WARDEN_RANGE = 16;

    private final Task searchSensorTask = new SearchChunkForBlockTask(Blocks.SCULK_SENSOR);
    private final TimerGame sneakTimer = new TimerGame(1);
    private BlockPos sensor;
    private boolean sneaking;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        sensor = null;
        sneaking = false;
        finished = false;
        successful = false;
        AltoClef.getInstance().getInputControls().release(Input.SNEAK);
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        Optional<WardenEntity> nearbyWarden = mod.getEntityTracker()
                .getTrackedEntities(WardenEntity.class).stream()
                .filter(Entity::isAlive)
                .filter(warden -> warden.squaredDistanceTo(mod.getPlayer()) <= WARDEN_RANGE * WARDEN_RANGE)
                .min(Comparator.comparingDouble(warden -> warden.squaredDistanceTo(mod.getPlayer())));
        if (nearbyWarden.isPresent()) {
            return sneakNearTarget(mod, "warden");
        }

        if (sensor != null && !mod.getWorld().getBlockState(sensor).isOf(Blocks.SCULK_SENSOR)) {
            sensor = null;
            stopSneaking(mod);
        }
        if (sensor == null) {
            sensor = mod.getBlockScanner().getNearestBlock(Blocks.SCULK_SENSOR).orElse(null);
        }
        if (sensor == null) {
            stopSneaking(mod);
            setDebugState("Searching for a sculk sensor");
            return searchSensorTask;
        }

        double distanceSquared = mod.getPlayer().squaredDistanceTo(sensor.toCenterPos());
        if (distanceSquared > SENSOR_RANGE * SENSOR_RANGE) {
            stopSneaking(mod);
            setDebugState("Approaching a sculk sensor");
            return new GetToBlockTask(sensor);
        }
        return sneakNearTarget(mod, "sculk sensor");
    }

    private Task sneakNearTarget(AltoClef mod, String targetName) {
        if (!sneaking) {
            mod.getInputControls().hold(Input.SNEAK);
            sneakTimer.reset();
            sneaking = true;
        }
        if (sneakTimer.elapsed()) {
            successful = true;
            finished = true;
            stopSneaking(mod);
            return null;
        }
        setDebugState("Sneaking within range of a " + targetName);
        return null;
    }

    private void stopSneaking(AltoClef mod) {
        if (sneaking) {
            mod.getInputControls().release(Input.SNEAK);
            sneaking = false;
        }
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
        AltoClef.getInstance().getInputControls().release(Input.SNEAK);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof Sneak100Task;
    }

    @Override
    protected String toDebugString() {
        return "Sneaking near a sculk sensor or Warden";
    }
}

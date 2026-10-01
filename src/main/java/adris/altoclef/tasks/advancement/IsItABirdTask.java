package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.SearchWithinBiomeTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.BiomeKeys;

import java.util.Comparator;

public final class IsItABirdTask extends Task {
    private static final double SPYGLASS_RANGE = 64;
    private final TimerGame spyglassTimer = new TimerGame(2);
    private final SearchWithinBiomeTask jungleSearchTask = new SearchWithinBiomeTask(BiomeKeys.JUNGLE);
    private ParrotEntity target;
    private Task approachTask;
    private boolean usingSpyglass;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        approachTask = null;
        usingSpyglass = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            stopUsingSpyglass(mod);
            setDebugState("Returning to the Overworld to find a parrot");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!mod.getItemStorage().hasItem(Items.SPYGLASS)) {
            stopUsingSpyglass(mod);
            if (!TaskCatalogue.taskExists(Items.SPYGLASS)) {
                mod.logWarning("Cannot complete Is It a Bird?: the spyglass recipe is unavailable.");
                finished = true;
                return null;
            }
            setDebugState("Crafting a spyglass");
            return TaskCatalogue.getItemTask(Items.SPYGLASS, 1);
        }

        if (target == null || !target.isAlive()) {
            target = mod.getEntityTracker().getTrackedEntities(ParrotEntity.class).stream()
                    .filter(ParrotEntity::isAlive)
                    .min(Comparator.comparingDouble(parrot -> parrot.squaredDistanceTo(mod.getPlayer())))
                    .orElse(null);
            approachTask = null;
        }

        if (target == null) {
            stopUsingSpyglass(mod);
            setDebugState("Searching jungle biomes for a parrot");
            return jungleSearchTask;
        }

        double distance = mod.getPlayer().squaredDistanceTo(target);
        if (distance > SPYGLASS_RANGE * SPYGLASS_RANGE) {
            stopUsingSpyglass(mod);
            if (approachTask == null) approachTask = new GetToEntityTask(target, SPYGLASS_RANGE - 2);
            setDebugState("Approaching a parrot");
            return approachTask;
        }

        Vec3d targetPosition = target.getEyePos();
        baritone.api.utils.Rotation lookRotation = LookHelper.getLookRotation(mod, targetPosition);
        LookHelper.lookAt(mod, targetPosition, false);
        if (!LookHelper.isLookingAt(mod, lookRotation)) {
            stopUsingSpyglass(mod);
            setDebugState("Aiming the spyglass at the parrot");
            return null;
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.SPYGLASS)) {
            stopUsingSpyglass(mod);
            return null;
        }
        if (!(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult hit)
                || hit.getEntity() != target) {
            stopUsingSpyglass(mod);
            setDebugState("Aligning the spyglass precisely with the parrot");
            return null;
        }
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        if (!usingSpyglass) {
            usingSpyglass = true;
            spyglassTimer.reset();
        }
        setDebugState("Looking at the parrot through a spyglass");
        if (spyglassTimer.elapsed()) {
            successful = true;
            finished = true;
            stopUsingSpyglass(mod);
        }
        return null;
    }

    private void stopUsingSpyglass(AltoClef mod) {
        if (usingSpyglass) {
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getPlayer().stopUsingItem();
            usingSpyglass = false;
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
        stopUsingSpyglass(AltoClef.getInstance());
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof IsItABirdTask;
    }

    @Override
    protected String toDebugString() {
        return "Looking at a parrot through a spyglass";
    }
}

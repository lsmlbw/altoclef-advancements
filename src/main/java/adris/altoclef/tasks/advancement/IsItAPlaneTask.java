package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
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
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

public final class IsItAPlaneTask extends Task {
    private static final double SPYGLASS_RANGE = 64;
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/spyglass_at_dragon");

    private final DragonRespawnTask dragonRespawnTask;
    private final TimerGame spyglassTimer = new TimerGame(2);
    private final TimeoutWanderTask endSearchTask = new TimeoutWanderTask(true);
    private EnderDragonEntity target;
    private Task approachTask;
    private boolean usingSpyglass;
    private boolean finished;
    private boolean successful;

    public IsItAPlaneTask(DragonRespawnTask dragonRespawnTask) {
        this.dragonRespawnTask = dragonRespawnTask;
    }

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

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Is It a Plane? advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            stopUsingSpyglass(mod);
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.END) {
            stopUsingSpyglass(mod);
            setDebugState("Returning to the End to look at the Ender Dragon");
            return new DefaultGoToDimensionTask(Dimension.END);
        }

        if (!mod.getItemStorage().hasItem(Items.SPYGLASS)) {
            stopUsingSpyglass(mod);
            if (!TaskCatalogue.taskExists(Items.SPYGLASS)) {
                return fail(mod, "the spyglass recipe is unavailable");
            }
            setDebugState("Crafting a spyglass for Is It a Plane?");
            return TaskCatalogue.getItemTask(Items.SPYGLASS, 1);
        }

        if (target == null || !target.isAlive()) {
            target = mod.getEntityTracker().getTrackedEntities(EnderDragonEntity.class).stream()
                    .filter(EnderDragonEntity::isAlive)
                    .min(Comparator.comparingDouble(dragon -> dragon.squaredDistanceTo(mod.getPlayer())))
                    .orElse(null);
            approachTask = null;
        }

        if (target == null) {
            stopUsingSpyglass(mod);
            if (dragonRespawnTask.isFinished() && !dragonRespawnTask.wasSuccessful()) {
                return fail(mod, "no Ender Dragon is available after the dragon respawn task");
            }
            setDebugState("Searching the End for an Ender Dragon");
            return endSearchTask;
        }

        if (mod.getPlayer().squaredDistanceTo(target) > SPYGLASS_RANGE * SPYGLASS_RANGE) {
            stopUsingSpyglass(mod);
            if (approachTask == null) {
                approachTask = new GetToEntityTask(target, SPYGLASS_RANGE - 2);
            }
            setDebugState("Approaching the Ender Dragon");
            return approachTask;
        }

        Vec3d targetPosition = target.getEyePos();
        baritone.api.utils.Rotation lookRotation = LookHelper.getLookRotation(mod, targetPosition);
        LookHelper.lookAt(mod, targetPosition, false);
        if (!LookHelper.isLookingAt(mod, lookRotation)) {
            stopUsingSpyglass(mod);
            setDebugState("Aiming the spyglass at the Ender Dragon");
            return null;
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.SPYGLASS)) {
            stopUsingSpyglass(mod);
            return null;
        }
        if (!(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult hit)
                || !isTargetDragon(hit.getEntity())) {
            stopUsingSpyglass(mod);
            setDebugState("Aligning the spyglass precisely with the Ender Dragon");
            return null;
        }

        mod.getInputControls().hold(Input.CLICK_RIGHT);
        if (!usingSpyglass) {
            usingSpyglass = true;
            spyglassTimer.reset();
        }
        setDebugState("Looking at the Ender Dragon through a spyglass");
        if (spyglassTimer.elapsed()) stopUsingSpyglass(mod);
        return null;
    }

    private boolean isTargetDragon(Entity entity) {
        return entity == target || entity instanceof EnderDragonPart;
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
        mod.logWarning("Cannot complete Is It a Plane?: " + reason + ".");
        finished = true;
        stopUsingSpyglass(mod);
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
        return other instanceof IsItAPlaneTask;
    }

    @Override
    protected String toDebugString() {
        return "Looking at the Ender Dragon through a spyglass";
    }
}

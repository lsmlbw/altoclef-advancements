package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

public final class TakeAimTask extends Task {
    private static final double MAX_SHOT_RANGE = 18;

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame drawTimer = new TimerGame(1.2);
    private final TimerGame hitTimer = new TimerGame(4);
    private LivingEntity target;
    private float targetHealthBeforeShot;
    private boolean drawing;
    private boolean waitingForHit;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        targetHealthBeforeShot = 0;
        drawing = false;
        waitingForHit = false;
        finished = false;
        successful = false;
        releaseBow();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (waitingForHit) {
            if (target != null && target.getHealth() < targetHealthBeforeShot) {
                successful = true;
                finished = true;
                return null;
            }
            if (!hitTimer.elapsed()) {
                setDebugState("Waiting for the arrow to hit its target");
                return null;
            }
            waitingForHit = false;
            target = null;
        }

        if (!mod.getItemStorage().hasItem(Items.BOW)) {
            releaseBow();
            if (!TaskCatalogue.taskExists(Items.BOW)) {
                mod.logWarning("Cannot complete Take Aim: the bow resource task is unavailable.");
                finished = true;
                return null;
            }
            setDebugState("Obtaining a bow");
            return TaskCatalogue.getItemTask(Items.BOW, 1);
        }

        if (!hasArrow(mod)) {
            releaseBow();
            if (!TaskCatalogue.taskExists(Items.ARROW)) {
                mod.logWarning("Cannot complete Take Aim: the arrow resource task is unavailable.");
                finished = true;
                return null;
            }
            setDebugState("Obtaining an arrow");
            return TaskCatalogue.getItemTask(Items.ARROW, 1);
        }

        if (target == null || !target.isAlive()) target = findTarget(mod);
        if (target == null) {
            releaseBow();
            setDebugState("Searching for a mob to shoot");
            return exploreTask;
        }

        double distance = mod.getPlayer().squaredDistanceTo(target);
        if (distance > MAX_SHOT_RANGE * MAX_SHOT_RANGE) {
            releaseBow();
            setDebugState("Approaching the mob to get it within bow range");
            return new GetToEntityTask(target, MAX_SHOT_RANGE - 2);
        }

        Vec3d aimPosition = target.getBoundingBox().getCenter();
        baritone.api.utils.Rotation aimRotation = LookHelper.getLookRotation(mod, aimPosition);
        LookHelper.lookAt(mod, aimPosition, false);
        if (!LookHelper.isLookingAt(mod, aimRotation)) {
            releaseBow();
            setDebugState("Aiming the bow at the mob");
            return null;
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.BOW)) {
            releaseBow();
            return null;
        }

        if (!(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult hit)
                || hit.getEntity() != target) {
            releaseBow();
            setDebugState("Aligning the bow directly with the mob");
            return null;
        }

        if (!drawing) {
            targetHealthBeforeShot = target.getHealth();
            drawing = true;
            drawTimer.reset();
        }
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        if (!drawTimer.elapsed()) {
            setDebugState("Drawing the bow");
            return null;
        }

        releaseBow();
        drawing = false;
        waitingForHit = true;
        hitTimer.reset();
        setDebugState("Firing an arrow at the mob");
        return null;
    }

    private boolean hasArrow(AltoClef mod) {
        return mod.getItemStorage().hasItem(Items.ARROW)
                || mod.getItemStorage().hasItem(Items.SPECTRAL_ARROW)
                || mod.getItemStorage().hasItem(Items.TIPPED_ARROW);
    }

    private LivingEntity findTarget(AltoClef mod) {
        LivingEntity closestAnimal = null;
        LivingEntity closestMonster = null;
        double animalDistance = Double.POSITIVE_INFINITY;
        double monsterDistance = Double.POSITIVE_INFINITY;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()
                    || living == mod.getPlayer() || living instanceof WardenEntity) {
                continue;
            }
            double distance = living.squaredDistanceTo(mod.getPlayer());
            if (living instanceof AnimalEntity animal && !animal.isBaby() && distance < animalDistance) {
                closestAnimal = living;
                animalDistance = distance;
            } else if (living instanceof Monster && distance < monsterDistance) {
                closestMonster = living;
                monsterDistance = distance;
            }
        }
        return closestAnimal != null ? closestAnimal : closestMonster;
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
        return other instanceof TakeAimTask;
    }

    @Override
    protected String toDebugString() {
        return "Shooting a mob with an arrow";
    }
}

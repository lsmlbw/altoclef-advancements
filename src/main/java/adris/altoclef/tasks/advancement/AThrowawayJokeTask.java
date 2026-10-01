package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Items;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

public final class AThrowawayJokeTask extends Task {
    private static final double THROW_RANGE = 24;
    private static final int TRIDENT_RECOVERY_TIMEOUT_SECONDS = 15;
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame chargeTimer = new TimerGame(2);
    private final TimerGame hitTimeout = new TimerGame(4);
    private final TimerGame recoveryTimer = new TimerGame(TRIDENT_RECOVERY_TIMEOUT_SECONDS);
    private LivingEntity target;
    private DrownedEntity tridentDrowned;
    private Task activeTask;
    private float targetHealthBeforeThrow;
    private boolean charging;
    private boolean waitingForHit;
    private boolean hitConfirmed;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        tridentDrowned = null;
        activeTask = null;
        targetHealthBeforeThrow = 0;
        charging = false;
        waitingForHit = false;
        hitConfirmed = false;
        finished = false;
        successful = false;
        recoveryTimer.reset();
        releaseUse();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (waitingForHit) {
            if (target != null && (target.getHealth() < targetHealthBeforeThrow || !target.isAlive())) {
                successful = true;
                hitConfirmed = true;
                waitingForHit = false;
                recoveryTimer.reset();
                setDebugState("Trident hit confirmed; recovering it");
                return null;
            }
            if (!hitTimeout.elapsed()) {
                setDebugState("Waiting for the thrown trident to hit its target");
                return null;
            }
            waitingForHit = false;
            target = null;
            setDebugState("The trident missed; recovering it before retrying");
        }

        if (hitConfirmed) {
            if (mod.getItemStorage().hasItem(Items.TRIDENT)) {
                finished = true;
                return null;
            }
            if (mod.getEntityTracker().itemDropped(Items.TRIDENT)) {
                setDebugState("Picking up the trident after the hit");
                return new PickupDroppedItemTask(new ItemTarget(Items.TRIDENT, 1), true);
            }
            TridentEntity thrownTrident = findThrownTrident(mod);
            if (thrownTrident != null) {
                setDebugState("Retrieving the thrown trident");
                return new GetToEntityTask(thrownTrident, 1.5);
            }
            if (recoveryTimer.elapsed()) {
                mod.logWarning("A Throwaway Joke was completed, but the thrown trident could not be recovered.");
                finished = true;
                return null;
            }
            setDebugState("Waiting for the thrown trident to become recoverable");
            return null;
        }

        if (!mod.getItemStorage().hasItem(Items.TRIDENT)) {
            releaseUse();
            charging = false;
            target = null;
            if (mod.getEntityTracker().itemDropped(Items.TRIDENT)) {
                setDebugState("Recovering the thrown or dropped trident");
                return new PickupDroppedItemTask(new ItemTarget(Items.TRIDENT, 1), true);
            }
            if (tridentDrowned != null && tridentDrowned.isAlive()) {
                setDebugState("Defeating a drowned carrying a trident");
                activeTask = new KillEntityTask(tridentDrowned);
                return activeTask;
            }
            if (tridentDrowned != null && !tridentDrowned.isAlive()) {
                tridentDrowned = null;
            }
            tridentDrowned = findTridentDrowned(mod);
            if (tridentDrowned == null) {
                if (!TaskCatalogue.taskExists(Items.TRIDENT)) {
                    setDebugState("Searching for a drowned carrying a trident");
                    return exploreTask;
                }
                setDebugState("Obtaining a trident");
                return TaskCatalogue.getItemTask(Items.TRIDENT, 1);
            }
            setDebugState("Approaching a drowned carrying a trident");
            activeTask = new GetToEntityTask(tridentDrowned, 3);
            return activeTask;
        }

        if (target == null || !target.isAlive()) {
            target = findTarget(mod);
        }
        if (target == null) {
            releaseUse();
            charging = false;
            setDebugState("Searching for a mob to throw the trident at");
            return exploreTask;
        }

        if (mod.getPlayer().squaredDistanceTo(target) > THROW_RANGE * THROW_RANGE) {
            releaseUse();
            charging = false;
            setDebugState("Approaching a mob within trident range");
            return new GetToEntityTask(target, THROW_RANGE - 2);
        }

        Vec3d targetPosition = target.getEyePos();
        baritone.api.utils.Rotation lookRotation = LookHelper.getLookRotation(mod, targetPosition);
        LookHelper.lookAt(mod, targetPosition, false);
        if (!LookHelper.isLookingAt(mod, lookRotation)
                || !(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult hit)
                || hit.getEntity() != target) {
            releaseUse();
            charging = false;
            setDebugState("Aiming directly at the mob");
            return null;
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.TRIDENT)) {
            releaseUse();
            charging = false;
            return null;
        }

        if (!charging) {
            targetHealthBeforeThrow = target.getHealth();
            charging = true;
            chargeTimer.reset();
        }
        if (!chargeTimer.elapsed()) {
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            setDebugState("Charging the trident throw");
            return null;
        }

        releaseUse();
        charging = false;
        waitingForHit = true;
        hitTimeout.reset();
        setDebugState("Throwing the trident at the mob");
        return null;
    }

    private DrownedEntity findTridentDrowned(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(DrownedEntity.class).stream()
                .filter(DrownedEntity::isAlive)
                .filter(drowned -> drowned.getMainHandStack().isOf(Items.TRIDENT))
                .min((first, second) -> Double.compare(
                        first.squaredDistanceTo(mod.getPlayer()),
                        second.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private LivingEntity findTarget(AltoClef mod) {
        return findClosestTarget(mod);
    }

    private LivingEntity findClosestTarget(AltoClef mod) {
        LivingEntity closestAnimal = null;
        LivingEntity closestMonster = null;
        double animalDistance = Double.POSITIVE_INFINITY;
        double monsterDistance = Double.POSITIVE_INFINITY;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living == mod.getPlayer()) {
                continue;
            }
            double distance = living.squaredDistanceTo(mod.getPlayer());
            if (living instanceof AnimalEntity && distance < animalDistance) {
                closestAnimal = living;
                animalDistance = distance;
            } else if (living instanceof Monster && distance < monsterDistance) {
                closestMonster = living;
                monsterDistance = distance;
            }
        }
        return closestAnimal != null ? closestAnimal : closestMonster;
    }

    private TridentEntity findThrownTrident(AltoClef mod) {
        TridentEntity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!(entity instanceof TridentEntity trident) || trident.getOwner() != mod.getPlayer()) continue;
            double distance = trident.squaredDistanceTo(mod.getPlayer());
            if (distance < closestDistance) {
                closest = trident;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private void releaseUse() {
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
        releaseUse();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AThrowawayJokeTask;
    }

    @Override
    protected String toDebugString() {
        return "Throwing a trident at a mob";
    }
}

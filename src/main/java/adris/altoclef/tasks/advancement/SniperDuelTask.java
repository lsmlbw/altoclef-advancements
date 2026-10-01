package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

public final class SniperDuelTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/sniper_duel");
    private static final double REQUIRED_HORIZONTAL_DISTANCE = 50;
    private static final double FIRING_DISTANCE = 60;
    private static final float WEAKEN_TO_HEALTH = 5;
    private static final double[] AIM_HEIGHT_FACTORS = {0.12, 0.25, 0.38, 0.52, 0.68};

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final Task experienceTask = new KillEntitiesTask(net.minecraft.entity.mob.Monster.class);
    private final TimerGame drawTimer = new TimerGame(1.2);
    private final TimerGame hitTimer = new TimerGame(5);
    private SkeletonEntity target;
    private Task movementTask;
    private BlockPos firingPosition;
    private float targetHealthBeforeShot;
    private boolean drawing;
    private boolean waitingForHit;
    private int aimAttempt;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        movementTask = null;
        firingPosition = null;
        targetHealthBeforeShot = 0;
        drawing = false;
        waitingForHit = false;
        aimAttempt = 0;
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
            setDebugState("Waiting for Sniper Duel advancement progress from the server");
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
            setDebugState("Returning to the Overworld to hunt a skeleton for Sniper Duel");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (waitingForHit) {
            if (target != null && (!target.isAlive() || target.getHealth() < targetHealthBeforeShot)) {
                waitingForHit = false;
                drawing = false;
                aimAttempt = Math.min(aimAttempt + 1, AIM_HEIGHT_FACTORS.length - 1);
                if (target.isAlive()) {
                    setDebugState("Arrow hit the skeleton; weakening it further before the next shot");
                }
                return null;
            }
            if (!hitTimer.elapsed()) {
                setDebugState("Waiting for the long-range arrow to hit the skeleton");
                return null;
            }
            waitingForHit = false;
            drawing = false;
            aimAttempt = (aimAttempt + 1) % AIM_HEIGHT_FACTORS.length;
        }

        if (!mod.getItemStorage().hasItem(Items.BOW)) {
            releaseBow();
            return getResource(mod, Items.BOW, "Obtaining a bow for Sniper Duel");
        }
        if (!hasArrow(mod)) {
            releaseBow();
            return getResource(mod, Items.ARROW, "Obtaining arrows for Sniper Duel");
        }

        if (target == null || !target.isAlive()) {
            target = findSkeleton(mod);
            movementTask = null;
            firingPosition = null;
            aimAttempt = 0;
        }
        if (target == null) {
            releaseBow();
            setDebugState("Searching for a regular skeleton");
            return exploreTask;
        }

        if (target.getHealth() > WEAKEN_TO_HEALTH && !waitingForHit) {
            releaseBow();
            if (mod.getPlayer().squaredDistanceTo(target) > 3.0 * 3.0) {
                setDebugState("Approaching the skeleton to weaken it safely");
                return new GetToEntityTask(target, 2.5);
            }
            if (!mod.getSlotHandler().forceDeequip(stack -> !stack.isEmpty())) return null;
            LookHelper.lookAt(mod, target.getBoundingBox().getCenter());
            if (mod.getPlayer().getAttackCooldownProgress(0) >= 1 && mod.getPlayer().isOnGround()) {
                mod.getControllerExtras().attack(target);
            }
            setDebugState("Weakening the skeleton before the long-range shot");
            return null;
        }

        if (horizontalDistance(mod.getPlayer().getPos(), target.getPos())
                < REQUIRED_HORIZONTAL_DISTANCE) {
            releaseBow();
            if (firingPosition == null || movementTask == null || movementTask.isFinished()) {
                firingPosition = calculateFiringPosition(mod, target);
                movementTask = new GetToBlockTask(firingPosition);
            }
            setDebugState("Moving at least 50 blocks horizontally away from the skeleton");
            return movementTask;
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.BOW)) {
            releaseBow();
            return null;
        }

        Vec3d targetPosition = target.getBoundingBox().getCenter();
        double horizontalDistance = horizontalDistance(mod.getPlayer().getPos(), target.getPos());
        double aimHeight = horizontalDistance * AIM_HEIGHT_FACTORS[aimAttempt];
        Vec3d aimPoint = targetPosition.add(0, aimHeight, 0);
        baritone.api.utils.Rotation aimRotation = LookHelper.getLookRotation(mod, aimPoint);
        LookHelper.lookAt(mod, aimPoint, false);
        if (!LookHelper.isLookingAt(mod, aimRotation)) {
            releaseBow();
            setDebugState("Aiming the bow above the skeleton to account for arrow drop");
            return null;
        }

        if (!drawing) {
            targetHealthBeforeShot = target.getHealth();
            drawing = true;
            drawTimer.reset();
        }
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        if (!drawTimer.elapsed()) {
            setDebugState("Fully drawing the bow from over 50 blocks away");
            return null;
        }

        if (horizontalDistance(mod.getPlayer().getPos(), target.getPos())
                < REQUIRED_HORIZONTAL_DISTANCE) {
            releaseBow();
            return null;
        }
        releaseBow();
        mod.getInputControls().tryPress(Input.CLICK_RIGHT);
        waitingForHit = true;
        hitTimer.reset();
        setDebugState("Firing a projectile from more than 50 blocks away");
        return null;
    }

    private SkeletonEntity findSkeleton(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(SkeletonEntity.class).stream()
                .filter(skeleton -> skeleton.isAlive() && skeleton.getType() == EntityType.SKELETON)
                .min(Comparator.comparingDouble(skeleton -> skeleton.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private BlockPos calculateFiringPosition(AltoClef mod, SkeletonEntity skeleton) {
        Vec3d playerPosition = mod.getPlayer().getPos();
        Vec3d skeletonPosition = skeleton.getPos();
        double dx = playerPosition.x - skeletonPosition.x;
        double dz = playerPosition.z - skeletonPosition.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) {
            dx = 1;
            dz = 0;
            length = 1;
        }
        double scale = FIRING_DISTANCE / length;
        return BlockPos.ofFloored(skeletonPosition.x + dx * scale,
                playerPosition.y, skeletonPosition.z + dz * scale);
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
            mod.logWarning("Cannot complete Sniper Duel: no resource task is available for "
                    + item.getName().getString() + ".");
            finished = true;
            return null;
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
        return other instanceof SniperDuelTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing a skeleton from at least 50 blocks away";
    }
}
